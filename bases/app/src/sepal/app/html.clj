(ns sepal.app.html
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [dev.onionpancakes.chassis.core :as chassis]))

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
(defn static-url
  [static-file]
  (let [manifest (some-> manifest-file-path
                         (io/resource)
                         (slurp)
                         (json/read-str))
        static-file-path (str static-resource-folder fs/file-separator static-file)
        path (get-in manifest [static-file-path "file"])]
    (str static-root path)))

;; TODO: This should be cleaned up b/c we don't want always want the doctype.
;; Also the "render" methods do more than render.

(defn response [body]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (str chassis/doctype-html5 body)})

(defn render-html [html]
  (->> html
       (chassis/html)
       (response)))

(defn render-partial [html]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (str (chassis/html html))})
