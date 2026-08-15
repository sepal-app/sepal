(ns sepal.config.interface
  (:require [aero.core :as aero]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defmethod aero/reader 'ig/ref
  [_opts _tag value]
  (ig/ref value))

(defmethod aero/reader 'resource
  [_opts _tag value]
  (io/resource value))

(defn data-home
  "Where Sepal keeps its data. Priority: SEPAL_DATA_HOME > XDG_DATA_HOME/Sepal >
  platform default (macOS: ~/Library/Application Support/Sepal, else
  fs/xdg-data-home/Sepal). Takes an env map so it stays testable; the no-arg
  arity reads the real environment."
  ([] (data-home (System/getenv)))
  ([env]
   (or (get env "SEPAL_DATA_HOME")
       (when-let [xdg (get env "XDG_DATA_HOME")]
         (str (fs/path xdg "Sepal")))
       (if (= "Mac OS X" (System/getProperty "os.name"))
         (str (fs/path (System/getProperty "user.home") "Library" "Application Support" "Sepal"))
         (str (fs/path (fs/xdg-data-home) "Sepal"))))))

(defmethod aero/reader 'data-home
  [_opts _tag path-segment]
  (str (fs/path (data-home) path-segment)))

(defn read-config [config-file opts]
  (some-> (io/resource config-file)
          (aero/read-config opts)))
