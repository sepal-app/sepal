(ns sepal.error.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [failjure.core :as f]
            [malli.core :as m]
            [sepal.error.interface :as error.i])
  (:import [java.sql SQLException]))

(deftest test-error-is-a-failure
  (testing "an error answers failjure's protocol"
    (let [err (error.i/error ::broke "It broke")]
      (is (f/failed? err))
      (is (= "It broke" (f/message err)))))

  (testing "an exception is a failure too, which is what lets attempt-all stop on one"
    (is (f/failed? (Exception. "boom"))))

  (testing "an ordinary value is not a failure"
    (is (not (f/failed? {:a 1})))
    (is (not (f/failed? "ok")))
    (is (not (f/failed? nil)))))

(deftest test-error-accessors
  (let [err (error.i/error ::broke "It broke" {:extra 1})]
    (is (= ::broke (error.i/type err)))
    (is (= "It broke" (error.i/message err)))
    (is (= {:extra 1} (error.i/data err)))
    (is (error.i/error? err))
    (is (error.i/error? err ::broke))
    (is (not (error.i/error? err ::something-else)))
    (is (not (error.i/error? {:a 1})))
    (is (not (error.i/error? nil)))))

(deftest test-ex->error-on-a-coercion-failure
  (testing "carries the malli explain, so humanize names the field"
    (let [ex (try
               (m/coerce [:map [:name [:string {:min 1}]]] {:name ""} nil)
               (catch Exception e e))
          err (error.i/ex->error ex)]
      (is (error.i/error? err))
      (is (some? (error.i/explain err)))
      (is (contains? (error.i/humanize err) :name))
      (is (string? (f/message err))
          "f/message must be a string even though malli puts a keyword in :message"))))

(deftest test-ex->error-on-a-database-failure
  (testing "has no explain, so humanize returns nil and the caller falls back"
    (let [err (error.i/ex->error (SQLException. "database is locked"))]
      (is (error.i/error? err))
      (is (nil? (error.i/explain err)))
      (is (nil? (error.i/humanize err)))
      (is (= "database is locked" (f/message err))))))
