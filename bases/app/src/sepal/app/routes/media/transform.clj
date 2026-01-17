(ns sepal.app.routes.media.transform
  "Route handler for serving transformed/cached images."
  (:require [clojure.java.io :as io]
            [ring.util.response :as response]
            [sepal.aws-s3.interface :as s3.i]
            [sepal.media-transform.interface :as media-transform.i]
            [zodiac.core :as z])
  (:import [java.io File]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(def ^:private content-type-map
  {"jpg" "image/jpeg"
   "jpeg" "image/jpeg"
   "png" "image/png"
   "gif" "image/gif"
   "bmp" "image/bmp"})

(defn- file-extension [path]
  (let [s (str path)
        idx (.lastIndexOf s ".")]
    (when (pos? idx)
      (subs s (inc idx)))))

(defn- content-type-for-file [path]
  (get content-type-map (file-extension path) "application/octet-stream"))

(defn- create-temp-file
  "Create a temporary file path with the given extension.
   Returns a File object for a unique temp path (file does not exist yet)."
  [prefix ext]
  (let [suffix (str "." ext)
        path (Files/createTempFile prefix suffix (into-array FileAttribute []))
        file (.toFile path)]
    ;; Delete the empty file so S3 SDK can create it
    (.delete file)
    file))

(defn- download-from-s3
  "Download a media file from S3 to a temporary file.
   Returns the temp file path."
  [s3-client bucket s3-key]
  (let [ext (or (file-extension s3-key) "jpg")
        temp-file (create-temp-file "sepal-media-" ext)]
    (s3.i/get-object s3-client bucket s3-key temp-file)
    temp-file))

(defn- serve-file
  "Create a ring response serving the given file."
  [^File file download-filename]
  (cond-> (-> (response/file-response (.getPath file))
              (response/content-type (content-type-for-file file)))
    download-filename
    (response/header "Content-Disposition"
                     (str "attachment; filename=\"" download-filename "\""))))

(defn- serve-icon
  "Serve a file-type icon for non-image media."
  [_content-type]
  ;; TODO: Add actual icon files and serve them based on content-type
  ;; For now, return a simple placeholder response
  (-> (response/response "")
      (response/status 404)
      (response/content-type "text/plain")))

(defn handler
  "Handle image transform requests.
   
   Query params:
   - w: width (optional)
   - h: height (optional)
   - fit: 'crop' or 'contain' (default: contain)
   - q: quality 1-100 (default: 85)
   - fmt: 'jpg', 'png', or 'original' (default: original)
   - dl: filename to trigger download"
  [{:keys [::z/context query-params]}]
  (let [{:keys [s3-client media-upload-bucket media-transform-service resource]} context

        {:keys [cache-ds cache-dir]} media-transform-service
        {:keys [w h fit q fmt dl]} query-params
        media resource
        s3-key (:media/s3-key media)]

    (if (media-transform.i/image-content-type? (:media/media-type media))
      ;; Image - transform and serve
      (let [;; Parse params
            width (some-> w parse-long)
            height (some-> h parse-long)
            quality (some-> q parse-long)
            fit-kw (when fit (keyword fit))
            format-kw (when fmt (keyword fmt))

            ;; Build transform opts (only include non-nil values)
            opts (cond-> {}
                   width (assoc :width width)
                   height (assoc :height height)
                   fit-kw (assoc :fit fit-kw)
                   quality (assoc :quality quality)
                   format-kw (assoc :format format-kw))

            ;; Check if we need to transform or just serve original
            needs-transform? (or width height format-kw quality)]

        (if needs-transform?
          ;; Generate/fetch cached transform
          (let [media-id (:media/id media)
                ;; Download original from S3 to temp file
                temp-file (download-from-s3 s3-client media-upload-bucket s3-key)
                {:keys [path]} (try
                                 (media-transform.i/get-or-transform cache-ds cache-dir
                                                                     media-id temp-file opts)
                                 (finally
                                   ;; Clean up temp file
                                   (.delete temp-file)))]
            (serve-file (io/file path) dl))

          ;; No transform needed - serve original directly from S3
          (let [temp-file (download-from-s3 s3-client media-upload-bucket s3-key)]
            ;; Note: temp-file won't be deleted immediately, but that's ok for downloads
            ;; TODO: Consider streaming directly from S3 for large files
            (serve-file temp-file dl))))

      ;; Non-image - serve icon placeholder
      (serve-icon (:media/media-type media)))))
