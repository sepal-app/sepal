(ns sepal.app.csv-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.csv :as csv]))

(deftest rows->csv-test
  (testing "basic conversion with headers"
    (let [columns [{:key :name} {:key :age}]
          rows [{:name "Alice" :age 30}
                {:name "Bob" :age 25}]
          result (csv/rows->csv columns rows)]
      (is (= "name,age\nAlice,30\nBob,25\n" result))))

  (testing "custom headers via :header key"
    (let [columns [{:key :user/name :header "user_name"}
                   {:key :user/age :header "user_age"}]
          rows [{:user/name "Alice" :user/age 30}]
          result (csv/rows->csv columns rows)]
      (is (= "user_name,user_age\nAlice,30\n" result))))

  (testing "nil values become empty strings"
    (let [columns [{:key :name} {:key :email}]
          rows [{:name "Alice" :email nil}]
          result (csv/rows->csv columns rows)]
      (is (= "name,email\nAlice,\n" result))))

  (testing "boolean values become true/false strings"
    (let [columns [{:key :name} {:key :active}]
          rows [{:name "Alice" :active true}
                {:name "Bob" :active false}]
          result (csv/rows->csv columns rows)]
      (is (= "name,active\nAlice,true\nBob,false\n" result))))

  (testing "dates are passed through as-is when valid ISO-8601"
    (let [columns [{:key :name} {:key :date}]
          rows [{:name "Alice" :date "2024-01-15"}]
          result (csv/rows->csv columns rows)]
      (is (= "name,date\nAlice,2024-01-15\n" result))))

  (testing "empty rows produce header-only output"
    (let [columns [{:key :name} {:key :age}]
          rows []
          result (csv/rows->csv columns rows)]
      (is (= "name,age\n" result))))

  (testing "values with commas are quoted"
    (let [columns [{:key :name} {:key :notes}]
          rows [{:name "Alice" :notes "one, two, three"}]
          result (csv/rows->csv columns rows)]
      (is (= "name,notes\nAlice,\"one, two, three\"\n" result))))

  (testing "values with quotes are escaped"
    (let [columns [{:key :name} {:key :notes}]
          rows [{:name "Alice" :notes "said \"hello\""}]
          result (csv/rows->csv columns rows)]
      (is (= "name,notes\nAlice,\"said \"\"hello\"\"\"\n" result)))))
