(ns sepal.media-transform.core
  "Core image transformation logic using Thumbnailator."
  (:require [clojure.java.io :as io])
  (:import [java.io File]
           [net.coobird.thumbnailator Thumbnails]
           [net.coobird.thumbnailator.geometry Positions]))

(def ^:private default-quality 85)

(def ^:private format-extensions
  {:jpg "jpg"
   :png "png"})

(defn- output-format
  "Determine output format based on params and source file."
  [source-path {:keys [format]}]
  (if (or (nil? format) (= format :original))
    ;; Get extension from source
    (let [ext (-> source-path str (subs (inc (.lastIndexOf (str source-path) "."))))]
      (if (#{"jpg" "jpeg" "png" "gif" "bmp"} ext)
        ext
        "jpg"))  ; Default to jpg for unknown
    (get format-extensions format "jpg")))

(defn- apply-fit
  "Apply fit mode to Thumbnails builder."
  [builder fit]
  (case fit
    :crop (-> builder
              (.crop Positions/CENTER))
    :contain (-> builder
                 (.keepAspectRatio true))
    ;; Default to contain
    (-> builder
        (.keepAspectRatio true))))

(defn transform
  "Transform an image file and save to target path.
   
   Options:
   - :width    - Target width (required if height provided)
   - :height   - Target height (required if width provided)
   - :fit      - :crop or :contain (default :contain)
   - :quality  - JPEG quality 1-100 (default 85)
   - :format   - :jpg, :png, or :original (default :original)
   
   Returns the target path on success, throws on error."
  [source-path target-path {:keys [width height fit quality format]
                            :or {fit :contain
                                 quality default-quality}}]
  (let [source-file (io/file source-path)
        target-file (io/file target-path)
        out-format (output-format source-path {:format format})]

    ;; Ensure parent directory exists
    (when-let [parent (.getParentFile target-file)]
      (.mkdirs parent))

    (if (and width height)
      ;; Resize with dimensions
      (-> (Thumbnails/of ^"[Ljava.io.File;" (into-array File [source-file]))
          (.size width height)
          (apply-fit fit)
          (.outputFormat out-format)
          (.outputQuality (/ quality 100.0))
          (.toFile target-file))
      ;; No dimensions - just convert format/quality
      (-> (Thumbnails/of ^"[Ljava.io.File;" (into-array File [source-file]))
          (.scale 1.0)
          (.outputFormat out-format)
          (.outputQuality (/ quality 100.0))
          (.toFile target-file)))

    target-path))

(defn image-content-type?
  "Returns true if the content type is a supported image format."
  [content-type]
  (contains? #{"image/jpeg" "image/jpg" "image/png" "image/gif" "image/bmp"}
             content-type))
