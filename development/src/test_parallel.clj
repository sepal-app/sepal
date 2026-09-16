(ns test-parallel
  "Run unit tests in parallel across multiple threads.

   This uses Kaocha's experimental parallel support which runs tests
   in separate threads. Each test namespace creates its own temp database
   via the fixture, providing isolation.

   NOTE: This requires that test fixtures properly isolate state.
   The current fixture creates a new temp DB file per namespace load,
   so parallel execution at the namespace level should be safe."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]
            [integrant.core :as ig]
            [sepal.app.server] ;; Load ig/init-key methods
            [sepal.app.test.system :as test.system]
            [sepal.database.interface] ;; Load ig/init-key for schema
            [sepal.malli.interface] ;; Load ig/init-key for malli
            [sepal.test.interface :as test.i]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql])
  (:import [java.io File]))

(defn- read-test-paths
  "Read test paths from deps.edn :test alias."
  []
  (let [deps (read-string (slurp "deps.edn"))
        extra-paths (get-in deps [:aliases :test :extra-paths])]
    (or extra-paths [])))

(defn find-test-namespaces
  "Find all test namespace symbols by scanning test files in classpath."
  []
  (let [test-paths (read-test-paths)
        test-files (for [dir test-paths
                         :let [dir-file (io/file dir)]
                         :when (.exists dir-file)
                         file (file-seq dir-file)
                         :when (and (.isFile file)
                                    (str/ends-with? (.getName file) "_test.clj")
                                    (not (str/includes? (.getPath file) "integration")))]
                     file)]
    (->> test-files
         (map (fn [^File f]
                (let [content (slurp f)
                      ;; Extract namespace from (ns ...) form
                      ns-match (re-find #"\(ns\s+([^\s\)]+)" content)]
                  (when ns-match
                    (symbol (second ns-match))))))
         (remove nil?)
         (sort)
         vec)))

(defn partition-round-robin
  "Partition items into n groups using round-robin distribution."
  [n items]
  (let [groups (vec (repeat n []))]
    (reduce-kv
     (fn [acc idx item]
       (update acc (mod idx n) conj item))
     groups
     (vec items))))

(defn make-system-fixture
  "Create a fresh system fixture with its own temp database.
   This is needed for parallel execution where each thread needs isolation."
  []
  (let [db-path (.getAbsolutePath (File/createTempFile "sepal-test" ".db"))
        schema-dump-file (or (System/getenv "SCHEMA_DUMP_FILE") "db/schema.sql")
        system-config {:sepal.app.server/zodiac-sql {:database-path db-path
                                                     :pragmas {:journal_mode "WAL"
                                                               :foreign_keys "ON"}
                                                     :context-key :db}
                       :sepal.app.server/zodiac-assets {:build? false
                                                        :manifest-path "app/build/.vite/manifest.json"
                                                        :asset-resource-path "app/build/assets"
                                                        :package-json-dir "bases/app"}
                       :sepal.app.server/zodiac {:extensions [(ig/ref :sepal.app.server/zodiac-sql)
                                                              (ig/ref :sepal.app.server/zodiac-assets)]
                                                 :request-context {:forgot-password-email-from "support@sepal.app"
                                                                   :forgot-password-email-subject "Sepal - Reset Password"
                                                                   :reset-password-secret "1234"
                                                                   :app-domain "test.sepal.app"}
                                                 :cookie-secret "1234567890123456"
                                                 :start-server? false}
                       :sepal.database.interface/schema {:database-path db-path
                                                         :schema-dump-file schema-dump-file}
                       :sepal.malli.interface/init {}}]
    (test.i/create-system-fixture
     system-config
     (fn [system f]
       (let [db (-> system :sepal.app.server/zodiac ::z.sql/db)]
         (binding [test.system/*system* system
                   test.system/*db* db
                   test.system/*app* (-> system :sepal.app.server/zodiac ::z/app)
                   test.system/*cookie-store* (-> system :sepal.app.server/zodiac ::z/cookie-store)]
           (f))))
     (keys system-config))))

(defn run-namespace-tests
  "Run all tests in a namespace. Returns a map with results."
  [ns-sym]
  (require ns-sym :reload)
  (let [ns-obj (find-ns ns-sym)
        test-vars (->> (ns-publics ns-obj)
                       vals
                       (filter #(:test (meta %))))]
    (when (seq test-vars)
      (let [results (atom {:pass 0 :fail 0 :error 0})
            report-fn (fn [m]
                        (case (:type m)
                          :pass (swap! results update :pass inc)
                          :fail (swap! results update :fail inc)
                          :error (swap! results update :error inc)
                          nil))]
        (binding [test/*report-counters* (ref test/*initial-report-counters*)
                  test/report report-fn]
          (doseq [v test-vars]
            (test/test-var v)))
        @results))))

(defn run-group-with-fixture
  "Run a group of test namespaces with a fresh fixture."
  [group-id namespaces]
  (let [fixture (make-system-fixture)
        results (atom {:tests 0 :pass 0 :fail 0 :error 0 :namespaces [] :errors []})]
    (fixture
     (fn []
       (doseq [ns-sym namespaces]
         (try
           (let [ns-result (run-namespace-tests ns-sym)]
             (when ns-result
               (swap! results (fn [r]
                                (-> r
                                    (update :tests + (:pass ns-result) (:fail ns-result) (:error ns-result))
                                    (update :pass + (:pass ns-result))
                                    (update :fail + (:fail ns-result))
                                    (update :error + (:error ns-result))
                                    (update :namespaces conj ns-sym))))))
           (catch Exception e
             (swap! results (fn [r]
                              (-> r
                                  (update :error inc)
                                  (update :errors conj {:ns ns-sym :message (.getMessage e)}))))
             (println (format "Error in namespace %s: %s" ns-sym (.getMessage e)))
             (.printStackTrace e))))))
    (let [r @results]
      (assoc r
             :group-id group-id
             :success? (and (zero? (:fail r)) (zero? (:error r)))))))

(defn run-parallel
  "Run all unit tests in parallel across n threads.

   Each thread:
   1. Gets its own fresh system fixture with unique temp database
   2. Runs a subset of test namespaces sequentially within that fixture

   This provides full isolation between threads.

   Usage:
     (run-parallel)     ; Uses available processors
     (run-parallel 4)   ; Use 4 threads"
  ([]
   (run-parallel (.availableProcessors (Runtime/getRuntime))))
  ([parallelism]
   (let [all-namespaces (find-test-namespaces)
         ns-count (count all-namespaces)
         actual-parallelism (min parallelism ns-count)
         groups (partition-round-robin actual-parallelism all-namespaces)]

     (println (format "Running %d test namespaces across %d threads"
                      ns-count actual-parallelism))
     (println)

     ;; Run each group in a future (parallel thread)
     (let [start-time (System/currentTimeMillis)
           futures (mapv (fn [group-id namespaces]
                           (future
                             (println (format "Group %d: Starting %d namespaces..."
                                              group-id (count namespaces)))
                             (run-group-with-fixture group-id namespaces)))
                         (range)
                         groups)
           results (mapv deref futures)
           end-time (System/currentTimeMillis)
           elapsed-ms (- end-time start-time)
           all-passed? (every? :success? results)
           total-tests (reduce + (map :tests results))
           total-pass (reduce + (map :pass results))
           total-fail (reduce + (map :fail results))
           total-error (reduce + (map :error results))]

       (println)
       (println "=== Summary ===")
       (doseq [{:keys [group-id success? tests pass fail error namespaces]} results]
         (println (format "Group %d: %s (%d tests: %d pass, %d fail, %d error)"
                          group-id
                          (if success? "PASSED" "FAILED")
                          tests pass fail error)))

       (println)
       (println (format "Total: %d tests (%d pass, %d fail, %d error) in %.1f seconds"
                        total-tests total-pass total-fail total-error
                        (/ elapsed-ms 1000.0)))
       (println (if all-passed? "ALL PASSED" "SOME FAILED"))

       {:success? all-passed?
        :total-tests total-tests
        :total-pass total-pass
        :total-fail total-fail
        :total-error total-error
        :elapsed-ms elapsed-ms
        :results results}))))

(defn -main
  "Entry point for running from command line."
  [& args]
  (let [parallelism (if (seq args)
                      (Integer/parseInt (first args))
                      (.availableProcessors (Runtime/getRuntime)))
        result (run-parallel parallelism)]
    (System/exit (if (:success? result) 0 1))))

(comment
  ;; Run with default parallelism (CPU cores)
  (run-parallel)

  ;; Run with specific parallelism
  (run-parallel 4)
  (run-parallel 2)

  ;; Just find namespaces
  (find-test-namespaces)

  ;; Partition example
  (partition-round-robin 3 [:a :b :c :d :e :f :g])
  )
