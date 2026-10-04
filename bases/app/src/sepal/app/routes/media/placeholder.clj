(ns sepal.app.routes.media.placeholder
  "The image served in place of a preview the transform route can't make: a
  type it doesn't render, an original over the size caps, or one that fails to
  decode. A file icon, with the extension under it."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.util.response :as response]
            [sepal.app.ui.icons.lucide :as lucide]))

(def ^:private text-soft
  "Read from tokens.css, which is where colours live. An SVG loaded as an
  <img> can't see the page's custom properties."
  (or (some->> (io/resource "sepal/app/css/tokens.css")
               slurp
               (re-find #"--color-text-soft:\s*(#[0-9A-Fa-f]{3,8})")
               second)
      "currentColor"))

(def ^:private image-extensions
  #{"jpg" "jpeg" "png" "gif" "bmp" "tif" "tiff" "psd" "psb" "heic" "heif"
    "webp" "avif" "svg" "raw" "dng" "cr2" "nef"})

(defn- extension [s3-key]
  (some->> s3-key (re-find #"\.([A-Za-z0-9]{1,10})$") second str/lower-case))

(defn- image? [media-type ext]
  (or (some-> media-type (str/starts-with? "image/"))
      (contains? image-extensions ext)))

(defn svg
  "The placeholder for `media` at `width` by `height`. Transparent, so the
  tile or stage behind it shows through."
  [media width height]
  (let [ext (extension (:media/s3-key media))
        icon-size (-> (* 0.28 (min width height)) long (max 24) (min 96))
        font-size (max 10 (long (* 0.28 icon-size)))
        icon-x (quot (- width icon-size) 2)
        icon-y (- (quot (- height icon-size) 2) (if ext font-size 0))
        icon (if (image? (:media/media-type media) ext) lucide/file-image lucide/file)]
    (chassis/html
      [:svg {:xmlns "http://www.w3.org/2000/svg"
             :width width
             :height height
             :viewBox (str "0 0 " width " " height)}
       [:g {:transform (str "translate(" icon-x " " icon-y ")")
            :color text-soft}
        (icon :size icon-size)]
       (when ext
         [:text {:x (quot width 2)
                 :y (+ icon-y icon-size (* 2 font-size))
                 :text-anchor "middle"
                 :fill text-soft
                 :font-family "ui-sans-serif, system-ui, sans-serif"
                 :font-size font-size
                 :font-weight "600"
                 :letter-spacing "0.05em"}
          (str/upper-case ext)])])))

;; A day, not the year a real preview gets: what can be previewed changes as
;; this route improves, and the original never does.
(def ^:private cache-control "private, max-age=86400")

(defn response
  "The placeholder as a response, sized to the requested box."
  [media width height]
  (-> (response/response (svg media (or width 400) (or height 280)))
      (response/content-type "image/svg+xml")
      (response/header "Cache-Control" cache-control)))
