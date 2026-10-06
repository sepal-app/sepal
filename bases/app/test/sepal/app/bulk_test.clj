(ns sepal.app.bulk-test
  (:require [clojure.test :refer [deftest is testing]]
            [failjure.core :as f]
            [sepal.app.bulk :as bulk]
            [sepal.app.http-response :as http]
            [sepal.validation.interface :as validation.i]))

(def Params [:map {:closed true} [:ids bulk/Ids]])

(deftest test-ids-decode
  (testing "one id arrives as a string, many as a vector"
    (is (= {:ids [7]} (validation.i/validate-form-values Params {"ids" "7"})))
    (is (= {:ids [7 8]} (validation.i/validate-form-values Params {"ids" ["7" "8" "7"]}))))
  (testing "empty, non-numeric and too many are refused"
    (is (f/failed? (validation.i/validate-form-values Params {})))
    (is (f/failed? (validation.i/validate-form-values Params {"ids" "x"})))
    (is (f/failed? (validation.i/validate-form-values
                     Params {"ids" (mapv str (range 1 (+ 2 bulk/max-ids)))})))))

(deftest test-check-ids
  (is (nil? (bulk/check-ids {"ids" ["1" "2"]})))
  (let [halt (bulk/check-ids {})
        response (http/failure-response halt {:status 500})]
    (is (f/failed? halt))
    (is (= 422 (:status response)))
    (is (= [{:text "Select at least one row, and no more than 500."
             :category "error"}]
           (get-in response [:flash :messages])))))

(deftest test-refused
  (let [response (http/failure-response (bulk/refused "Nope.") {:status 500})]
    (is (= 422 (:status response)))
    (is (= "Nope." (get-in response [:flash :messages 0 :text])))))

(deftest test-applied
  (let [response (bulk/applied "Changed 3 materials.")]
    (is (= 200 (:status response)))
    (is (= "" (:body response)))
    (is (= "bulk-applied" (get-in response [:headers "HX-Trigger"])))
    (is (re-find #"text/html" (get-in response [:headers "Content-Type"])))
    (is (= "Changed 3 materials." (get-in response [:flash :messages 0 :text])))))

(deftest test-result-message
  (is (= "Changed 3." (bulk/result-message "Changed 3." "1 skipped." 0)))
  (is (= "Changed 3. 1 skipped." (bulk/result-message "Changed 3." "1 skipped." 1))))
