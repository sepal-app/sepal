(ns sepal.validation.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.error.interface :as error.i]
            [sepal.validation.interface :as validation.i]))

(deftest parse-date-test
  (testing "valid ISO-8601 date returns the date string"
    (is (= "2024-01-15" (validation.i/parse-date "2024-01-15")))
    (is (= "2024-12-31" (validation.i/parse-date "2024-12-31"))))

  (testing "empty string returns nil"
    (is (nil? (validation.i/parse-date ""))))

  (testing "nil returns nil"
    (is (nil? (validation.i/parse-date nil))))

  (testing "invalid format returns ::invalid-date"
    (is (= ::validation.i/invalid-date (validation.i/parse-date "not-a-date")))
    (is (= ::validation.i/invalid-date (validation.i/parse-date "01-15-2024")))
    (is (= ::validation.i/invalid-date (validation.i/parse-date "2024/01/15"))))

  (testing "impossible dates return ::invalid-date"
    (is (= ::validation.i/invalid-date (validation.i/parse-date "2024-02-30")))
    (is (= ::validation.i/invalid-date (validation.i/parse-date "2024-13-01")))))

(deftest date-schema-test
  (testing "valid date passes validation"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d "2024-01-15"})]
      (is (not (error.i/error? result)))
      (is (= {:d "2024-01-15"} result))))

  (testing "empty string becomes nil"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d ""})]
      (is (not (error.i/error? result)))
      (is (= {:d nil} result))))

  (testing "invalid date returns error"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d "bad-date"})]
      (is (error.i/error? result))
      (is (= {:d ["must be a valid date (YYYY-MM-DD)"]}
             (validation.i/humanize result)))))

  (testing "impossible date returns error"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d "2024-02-30"})]
      (is (error.i/error? result)))))
