(ns sepal.validation.interface-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [sepal.error.interface :as error.i]
            [sepal.validation.interface :as validation.i]))

(def TestSchema
  [:map {:closed true}
   [:code [:string {:min 1}]]
   [:taxon-id :int]])

(deftest test-validate-form-values-success
  (testing "validates and transforms valid form params"
    (let [form-params {"code" "ACC-001" "taxon-id" "123"}
          result (validation.i/validate-form-values TestSchema form-params)]
      (is (not (error.i/error? result)))
      (is (= {:code "ACC-001" :taxon-id 123} result)))))

(deftest test-validate-form-values-key-transformation
  (testing "converts string keys to keywords"
    (let [form-params {"code" "test" "taxon-id" "42"}
          result (validation.i/validate-form-values TestSchema form-params)]
      (is (contains? result :code))
      (is (contains? result :taxon-id)))))

(deftest test-validate-form-values-string-coercion
  (testing "coerces string values to proper types"
    (let [form-params {"code" "test" "taxon-id" "999"}
          result (validation.i/validate-form-values TestSchema form-params)]
      (is (int? (:taxon-id result)))
      (is (= 999 (:taxon-id result))))))

(deftest test-validate-form-values-validation-error
  (testing "returns error for invalid data"
    (let [form-params {"code" "" "taxon-id" "not-a-number"}
          result (validation.i/validate-form-values TestSchema form-params)]
      (is (error.i/error? result)))))

(deftest test-validate-form-values-humanized-errors
  (testing "humanize returns field error map"
    (let [form-params {"code" "" "taxon-id" "abc"}
          result (validation.i/validate-form-values TestSchema form-params)
          errors (validation.i/humanize result)]
      (is (map? errors))
      (is (contains? errors :code))
      (is (contains? errors :taxon-id))
      (is (vector? (:code errors)))
      (is (vector? (:taxon-id errors))))))

(deftest test-validate-form-values-missing-required
  (testing "returns error for missing required fields"
    (let [form-params {}
          result (validation.i/validate-form-values TestSchema form-params)]
      (is (error.i/error? result))
      (let [errors (validation.i/humanize result)]
        (is (contains? errors :code))
        (is (contains? errors :taxon-id))))))

(deftest test-validate-form-values-strips-extra-keys
  (testing "strips keys not in schema"
    (let [form-params {"code" "test" "taxon-id" "1" "extra-field" "ignored"}
          result (validation.i/validate-form-values TestSchema form-params)]
      (is (not (error.i/error? result)))
      (is (not (contains? result :extra-field))))))
