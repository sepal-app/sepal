(ns sepal.app.routes.media.transform
  "Route handler for serving transformed/cached images."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [failjure.core :as f]
            [ring.util.http-response :as http]
            [ring.util.response :as response]
            [sepal.app.routes.media.keys :as media.keys]
            [sepal.aws-s3.interface :as s3.i]
            [sepal.media-transform.interface :as media-transform.i]
            [sepal.validation.interface :as validation.i]
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

;; A media item's file never changes once uploaded, so a response for one id
;; and one set of params stays correct for good. Private: it is behind login.
(def ^:private cache-control "private, max-age=31536000, immutable")

(defn- with-headers [response download-filename]
  (cond-> (response/header response "Cache-Control" cache-control)
    download-filename
    (response/header "Content-Disposition"
                     (str "attachment; filename=\"" download-filename "\""))))

(defn- serve-file
  "Create a ring response serving the given file."
  [^File file download-filename]
  (some-> (response/file-response (.getPath file))
          (response/content-type (content-type-for-file file))
          (with-headers download-filename)))

(defn- serve-original
  "Stream the original from S3 rather than copying it to disk first."
  [s3-client bucket s3-key media-type download-filename]
  (let [{:keys [stream content-length]} (s3.i/get-object-stream s3-client bucket s3-key)]
    (-> (response/response stream)
        (response/content-type (or media-type (content-type-for-file s3-key)))
        (cond-> content-length (response/header "Content-Length" (str content-length)))
        (with-headers download-filename))))

(defn- serve-icon
  "Serve a file-type icon for non-image media."
  [_content-type]
  ;; TODO: Add actual icon files and serve them based on content-type
  ;; For now, return a simple placeholder response
  (-> (response/response "")
      (response/status 404)
      (response/content-type "text/plain")))

(def Params
  "Transform parameters, decoded from the string-keyed query map the way every
  other list route decodes its params. This used to destructure keyword keys
  straight off the raw query map, which never matched — so the transform branch
  never ran and every thumbnail was the full-size original."
  [:map {:closed true}
   [:w {:optional true} [:int {:min 1}]]
   [:h {:optional true} [:int {:min 1}]]
   [:fit {:optional true} [:string {:min 1}]]
   [:q {:optional true} [:int {:min 1 :max 100}]]
   [:fmt {:optional true} [:string {:min 1}]]
   [:dl {:optional true} [:string {:min 1}]]])

(defn handler
  "Handle image transform requests.
   
   Query params:
   - w: width (optional)
   - h: height (optional)
   - fit: 'crop' or 'contain' (default: contain)
   - q: quality 1-100 (default: 85)
   - fmt: 'jpg', 'png', or 'original' (default: original)
   - dl: filename to trigger download"
  [& {:keys [::z/context query-params]}]
  (let [{:keys [resource]} context]
    (if-not (media.keys/own-key? context resource)
      ;; Checked before the parameters, so a foreign key looks like no media at
      ;; all whatever else the request asked for.
      (do (log/warn "Refusing media outside this instance's prefix"
                    {:s3-key (:media/s3-key resource)})
          (http/not-found))
      (f/attempt-all
        [data (validation.i/validate-form-values Params query-params)]
        (let [{:keys [s3-client media-upload-bucket media-transform-service]} context
              {:keys [cache-ds cache-dir max-cache-size-bytes]} media-transform-service
              {:keys [w h fit q fmt dl]} data
              s3-key (:media/s3-key resource)]
          (if-not (media-transform.i/image-content-type? (:media/media-type resource))
            ;; Non-image - serve icon placeholder
            (serve-icon (:media/media-type resource))
            (let [width w
                  height h
                  quality q
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
                ;; The original is downloaded only on a cache miss.
                (let [temp-file (volatile! nil)
                      fetch (fn []
                              (vreset! temp-file
                                       (download-from-s3 s3-client media-upload-bucket s3-key)))
                      {:keys [path hit?]}
                      (try
                        (media-transform.i/get-or-transform cache-ds cache-dir
                                                            (:media/id resource)
                                                            (or (file-extension s3-key) "jpg")
                                                            fetch
                                                            opts)
                        (finally
                          (some-> ^File @temp-file .delete)))]
                  (when (and (not hit?) max-cache-size-bytes)
                    (media-transform.i/evict-lru! cache-ds cache-dir max-cache-size-bytes))
                  (serve-file (io/file path) dl))

                (serve-original s3-client media-upload-bucket s3-key
                                (:media/media-type resource) dl)))))
        (f/when-failed [e]
          (log/warn e "Rejecting a transform request with invalid parameters")
          (http/bad-request "Invalid transform parameters"))))))
