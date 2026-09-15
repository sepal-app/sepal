(ns sepal.app.http-response-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [sepal.app.http-response :as http]
            [sepal.error.interface :as error.i]
            [sepal.validation.interface :as validation.i])
  (:import [java.sql SQLException]))

(def ^:private fallback
  {:status 200 :headers {"HX-Redirect" "/taxon/new"} :body ""})

(deftest test-failure-response-with-field-errors
  (testing "a failure carrying a malli explain becomes a 422 naming the field"
    (let [err (validation.i/validate-form-values
                [:map {:closed true} [:name [:string {:min 1}]]]
                {"name" ""})
          response (http/failure-response err fallback)]
      (is (error.i/error? err) "the fixture has to be a failure to be meaningful")
      (is (= 422 (:status response)))
      (is (str/includes? (:body response) "name")))))

(deftest test-failure-response-without-field-errors
  (testing "a database failure has no explain, so the caller's fallback is returned"
    (let [err (error.i/ex->error (SQLException. "FOREIGN KEY constraint failed"))]
      (is (nil? (error.i/humanize err))
          "no explain, which is what used to produce an empty 422")
      (is (= fallback (http/failure-response err fallback)))))

  (testing "a raw exception is converted before the same decision"
    (is (= fallback
           (http/failure-response (SQLException. "FOREIGN KEY constraint failed")
                                  fallback)))))
