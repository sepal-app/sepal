(ns sepal.app.html
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [rum.core :as rum]
            [sepal.manifest.interface :as manifest.i]))


(def static-resource-folder "app/static")

(defn attr [& classes]
  (->> classes
       flatten
       (mapv name)
       (s/join " ")))

;; TODO: We need a debug mode to control when the regular file is returned and
;; when the digest file is returned is returned

(defn static-url [static-file]
  (let [static-file-resource (str (fs/path static-resource-folder static-file))
        manifest (some-> static-file-resource
                         (io/resource)
                         (fs/parent)
                         (manifest.i/manifest))]

    (->> (if-let [f (get manifest (fs/file-name static-file))]
           ;; The files in the manifest are only file names so return the file
           ;; relative to the parent of the static file.
           (fs/path (fs/parent static-file) f)
           static-file)
         ;; TODO: This actually needs access to the router to see the URL where
         ;; the static asset path is mapped...or we just assum "/static" unless its passed
         ;; (fs/path static-resource-folder)
         (fs/path "/static")
         (str))))


(defn html-response [body]
  {:status 200
   :headers {"content-type" "text/html"}
   :body body})

(defn render-html [html]
  (-> html
      (rum/render-static-markup)
      (html-response)))
