(ns sepal.logging.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [integrant.core :as ig]
            [sepal.logging.interface :as logging.i]
            [taoensso.telemere :as tel]))

;; sepal.* is a real namespace prefix here, not a placeholder: get-min-levels
;; resolves the effective level for a concrete namespace, and this one
;; happens to be sepal.logging.interface itself.
(def ^:private a-sepal-ns "sepal.logging.interface")

(defn- current-level []
  (:runtime (tel/get-min-levels nil a-sepal-ns)))

(deftest test-halting-restores-the-level-that-was-there-before
  (testing "init sets the configured level, halt puts back what governed sepal.* before it ran"
    (let [before (current-level)
          config (ig/init-key ::logging.i/logging {:level "WARN"})]
      (try
        (is (= :warn (current-level)) "the configured level took effect")
        (finally
          (ig/halt-key! ::logging.i/logging config)))
      (is (= before (current-level))
          "halt must not leave WARN in place for every test namespace that runs afterwards"))))

(deftest test-nested-init-halt-pairs-unwind-in-order
  ;; This is the shape start-process!/stop-process! actually produces when one
  ;; JVM runs more than one instance: each pair is self-contained, but they all
  ;; mutate the same global telemere state, so unwinding out of order would
  ;; restore the wrong level.
  (testing "the inner pair restores the outer level, not the original one"
    (let [before (current-level)
          outer (ig/init-key ::logging.i/logging {:level "WARN"})]
      (try
        (let [inner (ig/init-key ::logging.i/logging {:level "ERROR"})]
          (is (= :error (current-level)))
          (ig/halt-key! ::logging.i/logging inner)
          (is (= :warn (current-level))
              "unwinding the inner pair restores the outer pair's level"))
        (finally
          (ig/halt-key! ::logging.i/logging outer)))
      (is (= before (current-level))))))

(deftest test-no-level-configured-is-a-no-op
  (testing "omitting :level changes nothing, on init or on halt"
    (let [before (current-level)
          config (ig/init-key ::logging.i/logging {:level nil})]
      (is (= before (current-level)))
      (ig/halt-key! ::logging.i/logging config)
      (is (= before (current-level))))))
