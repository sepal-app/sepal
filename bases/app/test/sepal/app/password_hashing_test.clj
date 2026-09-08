(ns sepal.app.password-hashing-test
  "The scrypt work factor a real install hashes with.

  The test suite does not hash at this strength. One scrypt hash at these
  parameters costs 323 ms, and the suite hashes 529 times, which was 217 seconds
  of a 364-second run. The :test alias points password4j at a weak configuration
  through -Dpsw4j.configuration, so nothing in the suite measures the real cost.

  That override is exactly why this test exists. It reads the resource
  password4j loads when no override is set — which is every caller that is not
  the test suite — so weakening production hashing fails here even though the
  suite itself never hashes at production strength."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]])
  (:import [java.util Properties]))

(defn- production-hashing-properties
  "The psw4j.properties password4j reads off the classpath. Loaded as a resource
  rather than a file path so this asserts what password4j itself would find."
  []
  (with-open [in (io/input-stream (io/resource "psw4j.properties"))]
    (doto (Properties.) (.load in))))

(deftest test-production-scrypt-parameters
  (testing "a real install hashes at N=16384, r=16, p=1 with a 64-byte key"
    (let [props (production-hashing-properties)
          value (fn [k] (parse-long (.getProperty props k)))]
      (is (= 16384 (value "hash.scrypt.workfactor")))
      (is (= 16 (value "hash.scrypt.resources")))
      (is (= 1 (value "hash.scrypt.parallelization")))
      (is (= 64 (value "hash.scrypt.derivedKeyLength"))))))
