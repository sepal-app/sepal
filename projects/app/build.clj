(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def uber-file "target/sepal.jar")

(defn clean
  "Remove the target directory."
  [_]
  (b/delete {:path "target"}))

(defn uber
  "Build an uberjar for the application."
  [_]
  (clean nil)
  (let [basis (b/create-basis {:project "deps.edn"})]
    ;; Copy all source and resource directories from the basis
    ;; The basis includes paths from all :local/root dependencies
    (b/copy-dir {:src-dirs (:paths basis)
                 :target-dir class-dir})
    ;; AOT compile only the main entry point (minimal AOT)
    (b/compile-clj {:basis basis
                    :ns-compile '[sepal.app.main]
                    :class-dir class-dir})
    ;; Build the uberjar
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis
             :main 'sepal.app.main}))
  (println "Built:" uber-file))
