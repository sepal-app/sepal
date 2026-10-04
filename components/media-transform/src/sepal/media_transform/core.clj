(ns sepal.media-transform.core
  "Core image transformation logic using Thumbnailator."
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.awt Color]
           [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO ImageReader]
           [net.coobird.thumbnailator Thumbnails]
           [net.coobird.thumbnailator.geometry Positions]
           [net.coobird.thumbnailator.util.exif ExifFilterUtils ExifUtils Orientation]))

(def ^:private default-quality 85)

(def max-source-bytes
  "The largest original that is downloaded to make a preview."
  (* 100 1024 1024))

(def max-pixels
  "The most pixels an image may have and still be decoded. Read from the
  header, so an image over it costs no decoding."
  100000000)

(def ^:private web-extensions
  "Formats a browser shows, so a transform can keep the original's."
  #{"jpg" "jpeg" "png" "gif" "bmp"})

(def ^:private previewable-extensions
  (into web-extensions #{"psd" "tif" "tiff"}))

(def ^:private previewable-types
  #{"image/jpeg" "image/jpg" "image/png" "image/gif" "image/bmp"
    "image/tiff" "image/vnd.adobe.photoshop" "image/x-photoshop"
    "image/psd" "application/x-photoshop" "application/photoshop"
    "application/psd"})

(defn file-extension
  "The lower-cased extension of `path`, or nil."
  [path]
  (some->> path str (re-find #"\.([A-Za-z0-9]+)$") second str/lower-case))

(defn previewable?
  "Whether a preview can be made of a file of `media-type` stored at `path`.
  The extension counts as much as the type: browsers disagree on a PSD's type,
  and some send none."
  [media-type path]
  (or (contains? previewable-types (some-> media-type str/lower-case))
      (contains? previewable-extensions (file-extension path))))

(defn output-extension
  "The extension of a transform's output. :original keeps the source's format
  when a browser can show it, and is JPEG otherwise."
  [source-ext format]
  (if (or (nil? format) (= format :original))
    (let [ext (some-> source-ext str/lower-case)]
      (if (contains? web-extensions ext) ext "jpg"))
    (get {:jpg "jpg" :png "png"} format "jpg")))

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

(defn- subsampling
  "The largest whole step that still leaves the image at least twice the box
  it is scaled into, so a large original is never decoded at full size."
  [width height box-width box-height]
  (if (and box-width box-height)
    (max 1 (min (quot width (* 2 box-width)) (quot height (* 2 box-height))))
    1))

(def ^:private transposing-orientations
  "EXIF orientations that turn the image a quarter turn, swapping its sides."
  #{Orientation/LEFT_TOP Orientation/RIGHT_TOP
    Orientation/RIGHT_BOTTOM Orientation/LEFT_BOTTOM})

(defn- exif-orientation
  "The EXIF orientation of a JPEG, or nil. ExifUtils reads only JPEG metadata
  and throws on any other reader's."
  [^ImageReader reader]
  (when (= "jpeg" (str/lower-case (.getFormatName reader)))
    (ExifUtils/getExifOrientation reader 0)))

(defn- read-image
  "Decode the first image in `file`, reduced for a `box-width` by `box-height`
  box and turned the way its EXIF orientation says. Throws before decoding when
  the header names more than `max-pixels`."
  ^BufferedImage [^File file box-width box-height]
  (with-open [iis (ImageIO/createImageInputStream file)]
    (let [readers (some-> iis ImageIO/getImageReaders)]
      (when-not (and readers (.hasNext readers))
        (throw (ex-info "No reader for this image" {:file (str file)})))
      (let [^ImageReader reader (.next readers)]
        (try
          ;; Metadata is read, not ignored: the orientation is in it.
          (.setInput reader iis true false)
          (let [width (.getWidth reader 0)
                height (.getHeight reader 0)
                orientation (exif-orientation reader)
                [box-width box-height] (if (transposing-orientations orientation)
                                         [box-height box-width]
                                         [box-width box-height])
                step (subsampling width height box-width box-height)
                param (doto (.getDefaultReadParam reader)
                        (.setSourceSubsampling step step 0 0))]
            (when (> (* (long width) (long height)) max-pixels)
              (throw (ex-info "Image has too many pixels to preview"
                              {:width width :height height :max-pixels max-pixels})))
            (cond->> (.read reader 0 param)
              orientation (.apply (ExifFilterUtils/getFilterForOrientation orientation))))
          (finally
            (.dispose reader)))))))

(defn- flatten-onto-white
  "An opaque RGB copy of `image`. JPEG has no alpha, and a reader's own image
  type (a 16-bit PSD, a CMYK TIFF) is not one every writer takes."
  ^BufferedImage [^BufferedImage image]
  (let [out (BufferedImage. (.getWidth image) (.getHeight image) BufferedImage/TYPE_INT_RGB)
        g (.createGraphics out)]
    (try
      (.setColor g Color/WHITE)
      (.fillRect g 0 0 (.getWidth image) (.getHeight image))
      (.drawImage g image 0 0 nil)
      out
      (finally
        (.dispose g)))))

(defn transform
  "Transform an image file and save to target path.
   
   Options:
   - :width    - Target width (required if height provided)
   - :height   - Target height (required if width provided)
   - :fit      - :crop or :contain (default :contain)
   - :quality  - JPEG quality 1-100 (default 85)
   - :format   - :jpg, :png, or :original (default :original)
   
   Returns the target path on success, throws on error, including for an image
   over `max-pixels`."
  [source-path target-path {:keys [width height fit quality format]
                            :or {fit :contain
                                 quality default-quality}}]
  (let [source-file (io/file source-path)
        target-file (io/file target-path)
        out-format (output-extension (file-extension source-path) format)
        image (cond-> (read-image source-file width height)
                (#{"jpg" "jpeg"} out-format) flatten-onto-white)]

    ;; Ensure parent directory exists
    (when-let [parent (.getParentFile target-file)]
      (.mkdirs parent))

    (if (and width height)
      ;; Resize with dimensions
      (-> (Thumbnails/of ^"[Ljava.awt.image.BufferedImage;" (into-array BufferedImage [image]))
          (.size width height)
          (apply-fit fit)
          (.outputFormat out-format)
          (.outputQuality (/ quality 100.0))
          (.toFile target-file))
      ;; No dimensions - just convert format/quality
      (-> (Thumbnails/of ^"[Ljava.awt.image.BufferedImage;" (into-array BufferedImage [image]))
          (.scale 1.0)
          (.outputFormat out-format)
          (.outputQuality (/ quality 100.0))
          (.toFile target-file)))

    target-path))
