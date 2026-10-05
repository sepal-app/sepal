(ns sepal.app.list-columns-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.list-columns :as list-columns]))

(def columns
  [{:key :code :priority 1}
   {:key :taxon :priority 1}
   {:key :provenance :priority 3}
   {:key :supplier :priority 3 :hidden? true}])

(defn- keys-of [cols] (mapv :key cols))

(deftest test-defaults
  (is (= [:code :taxon :provenance] (keys-of (list-columns/visible-columns columns nil)))
      "no choice: every column not :hidden?"))

(deftest test-overrides
  (is (= [:code :taxon :supplier]
         (keys-of (list-columns/visible-columns columns {:provenance false :supplier true})))))

(deftest test-ignored-overrides
  (testing "a priority 1 column can't be hidden"
    (is (= [:code :taxon :provenance]
           (keys-of (list-columns/visible-columns columns {:code false})))))
  (testing "a key that matches no column changes nothing"
    (is (= [:code :taxon :provenance]
           (keys-of (list-columns/visible-columns columns {:gone true}))))))

(deftest test-valid-keys
  (is (list-columns/valid-keys? ["provenance" "quantity-received"]))
  (is (list-columns/valid-keys? []))
  (is (not (list-columns/valid-keys? ["Provenance"])))
  (is (not (list-columns/valid-keys? ["a b"])))
  (is (not (list-columns/valid-keys? (map #(str "k" %) (range 51))))))
