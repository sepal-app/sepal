(ns sepal.app.html
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [huff2.core :as huff]))

(defn attr [& classes]
  (->> classes
       flatten
       (mapv name)
       (s/join " ")))

;; The resource path for the built static files
(def static-dist-folder "app/dist")

;; The source folder of the static assets.
(def static-resource-folder "resources/app/static")

;; The root url path for the static assets
(def static-root "/")

(def manifest-file-path
  (str static-dist-folder
       fs/file-separator
       ".vite"
       fs/file-separator
       "manifest.json"))

;; TODO: We need to cache the manifest instead of parsing it on every request
;; similar to how manifest.i handled it.
(defn static-url [static-file]
  (let [manifest (some-> manifest-file-path
                         (io/resource)
                         (slurp)
                         (json/read-str))
        static-file-path (str static-resource-folder fs/file-separator static-file)
        path (get-in manifest [static-file-path "file"])]
    (str static-root path)))

;; TODO: This uses fs/glob to get the filename with the hash and will probably
;; be slow. We need to memoize this in production.
(defn image-url [f]
  (let [f "jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg"
        [head ext] (fs/split-ext f)
        asset-path (fs/path (io/resource static-dist-folder) "assets")
        pattern (str head "-*." ext)
        abs-path (-> (fs/glob asset-path pattern)
                     (first))]
    (str "assets/" (fs/file-name abs-path))))

;; TODO: This should be cleaned up b/c we don't want always want the doctype.
;; Also the "render" methods do more than render.

(defn response [body]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (str "<!DOCTYPE html>\n" body)})

(defn render-html [html]
  (->> html
       (huff/html)
       (response)))

(defn render-partial [html]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (huff/html html)})
