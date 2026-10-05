(ns sepal.app.list-query-test
  (:require [clojure.test :refer [deftest is testing]]
            [honey.sql :as sql]
            [sepal.app.list-query :as list-query]))

(def base {:select [:*] :from [[:accession :a]]})

(def supplier
  {:key :supplier
   :sort [[:lower :sc.name]]
   :query #(update % :left-join (fnil into []) [[:contact :sc] [:= :sc.id :a.supplier_contact_id]])})

(def code {:key :code :sort [:a.code]})
(def material-code {:key :code :sort [:a.code :m.code]})

(deftest test-with-columns
  (is (= base (list-query/with-columns base [code] nil)) "no :query, no change")
  (is (= [[:contact :sc] [:= :sc.id :a.supplier_contact_id]]
         (:left-join (list-query/with-columns base [code supplier] nil))))
  (testing "a hidden sort column's query still applies"
    (is (some? (:left-join (list-query/with-columns base [code] {:column supplier :dir :asc})))))
  (testing "a visible sort column's query applies once"
    (is (= 2 (count (:left-join (list-query/with-columns base [supplier] {:column supplier :dir :asc})))))))

(deftest test-order-by
  (let [opts {:relevance [[:rank :asc]] :default [[:a.code :asc]] :tiebreak [:a.id :asc]}]
    (is (= [[:rank :asc] [:a.code :asc]] (list-query/order-by nil opts))
        "no sort: relevance, then the default")
    (is (= [[[:lower :sc.name] :desc-nulls-last] [:a.id :asc]]
           (list-query/order-by {:column supplier :dir :desc} opts))
        "a sort replaces relevance and the default")
    (is (= [[:a.code :asc-nulls-last] [:m.code :asc-nulls-last] [:a.id :asc]]
           (list-query/order-by {:column material-code :dir :asc} opts))
        "every term of a compound sort")
    (is (= ["SELECT * FROM accession AS a ORDER BY LOWER(sc.name) DESC NULLS LAST, a.id ASC"]
           (sql/format (assoc base :order-by (list-query/order-by {:column supplier :dir :desc} opts))))
        "SQLite accepts what this renders")))
