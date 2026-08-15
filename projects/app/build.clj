(ns build
  (:require [clojure.java.io :as io]
            [clojure.tools.build.api :as b]))

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

(def lib 'com.github.sepal-app/sepal)
(def version (or (System/getenv "SEPAL_VERSION") "0.1.0-SNAPSHOT"))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn- flatten-component-deps
  "Clear :dependents on every lib a component or base declares directly, so
  write-pom treats it as this artifact's own dependency. A lib counts as
  directly declared if one of its :dependents is a :local/root coordinate
  (no :mvn/version) — components themselves stay skipped by write-pom either
  way, since they have no version to publish. Everything else keeps its
  :dependents, so write-pom continues to skip it and Maven resolves it
  transitively through the direct dep's own pom, same as any single-module
  project."
  [libs]
  (let [local-root? #(nil? (:mvn/version (libs %)))]
    (update-vals libs
                 (fn [{:keys [dependents] :as coord}]
                   (if (or (empty? dependents) (some local-root? dependents))
                     (dissoc coord :dependents)
                     coord)))))

(defn jar
  "Build a library jar for use by the control plane.

  Frontend assets must be built first — bin/build-uberjar.sh shows the order.
  Unlike `uber`, this doesn't AOT-compile anything, so it ships source: a
  consumer's own AOT and reloading behave normally.

  `(:paths basis)` only reflects this project's own deps.edn, which declares
  none, so it resolves to the tools.deps default of [\"src\"] — every
  :local/root component and base is missing. `:classpath-roots` has the full
  resolved classpath instead; filtering it to directories keeps each
  component's src/resources and drops the resolved third-party jars, which
  the pom lists as dependencies instead of inlining.

  `write-pom` only lists deps with no `:dependents`, i.e. deps declared
  directly in this deps.edn — but every third-party lib a component actually
  uses (sqlite-jdbc, integrant, zodiac, ...) is declared inside that
  component's own deps.edn instead, so tools.deps counts it as transitive
  through the component and write-pom drops it. `flatten-component-deps`
  promotes only those directly-declared libs to root; anything transitive
  beyond that keeps its :dependents. Passing plain absolute :src-dirs to
  write-pom would also bake this machine's paths into the published pom, so
  it's omitted there (copy-dir still needs the real paths, to actually copy
  the files)."
  [_]
  (clean nil)
  (let [basis (b/create-basis {:project "deps.edn"})
        src-dirs (filter #(.isDirectory (io/file %)) (:classpath-roots basis))
        pom-basis (update basis :libs flatten-component-deps)]
    (b/copy-dir {:src-dirs src-dirs :target-dir class-dir})
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis pom-basis})
    (b/jar {:class-dir class-dir :jar-file jar-file}))
  (println "Built:" jar-file))

(defn install
  "Install the library jar into the local Maven repository."
  [_]
  (jar nil)
  (b/install {:basis (b/create-basis {:project "deps.edn"})
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir})
  (println "Installed:" lib version))
