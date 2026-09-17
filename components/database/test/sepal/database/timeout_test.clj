(ns sepal.database.timeout-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [sepal.database.timeout :as timeout]))

(def ^:private spin
  "A query with no data behind it that SQLite will happily run for minutes.
  Counting to a hundred million is CPU, not IO, which is the case a
  setQueryTimeout does not cover."
  ["with recursive c(x) as (select 1 union all select x + 1 from c where x < 100000000)
    select count(*) as n from c"])

(defn- memory-db []
  (jdbc/with-options (jdbc/get-datasource {:dbtype "sqlite" :dbname ":memory:"})
    jdbc/snake-kebab-opts))

(deftest test-a-runaway-query-is-interrupted
  (testing "sqlite3_interrupt reaches a statement that is merely slow, which is
            what setQueryTimeout does not do"
    (let [started (System/currentTimeMillis)
          result (try
                   (timeout/execute! (memory-db) spin 500)
                   (catch Exception e e))
          elapsed (- (System/currentTimeMillis) started)]
      (is (instance? Exception result)
          "a query past its deadline fails rather than returning late")
      (is (< elapsed 5000)
          (str "should have been interrupted near 500ms, took " elapsed "ms")))))

(deftest test-a-quick-query-is-left-alone
  (testing "the deadline costs a fast query nothing"
    (is (= [{:n 1}] (timeout/execute! (memory-db) ["select 1 as n"] 5000)))))

(deftest test-the-row-count-is-bounded-too
  (testing "a list page counts over the same WHERE it selects over, so bounding
            one and not the other bounds nothing"
    (let [started (System/currentTimeMillis)
          result (try
                   (timeout/count (memory-db)
                                  {:select [:x]
                                   :from [[[:raw "(with recursive c(x) as (select 1 union all
                                                    select x + 1 from c where x < 100000000)
                                                    select x from c)"] :t]]}
                                  500)
                   (catch Exception e e))
          elapsed (- (System/currentTimeMillis) started)]
      (is (instance? Exception result))
      (is (< elapsed 5000) (str "took " elapsed "ms")))))

(deftest test-count-still-counts
  (is (= 2 (timeout/count (memory-db)
                          {:select [:x] :from [[[:raw "(select 1 as x union all select 2)"] :t]]}
                          5000))))
