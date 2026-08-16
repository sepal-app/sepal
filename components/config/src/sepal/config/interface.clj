(ns sepal.config.interface
  (:require [babashka.fs :as fs]))

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
