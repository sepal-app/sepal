(ns sepal.synonym.interface-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as err.i]
            [sepal.synonym.interface :as synonym.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(def ctx {:schema-version (db.i/latest-version)})

(deftest test-add-and-remove
  (tf/testing "a synonym round-trips"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [result (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                                 :synonym-name "Encyclia cochleata"})]
        (is (not (err.i/error? result)) (err.i/data result))
        (is (= "local" (:synonym/source result)))
        (is (= [(:synonym/id result)]
               (mapv :synonym/id (synonym.i/list-for-taxon ctx *db* (:taxon/id taxon)))))
        (synonym.i/remove-synonym! *db* (:synonym/id result))
        (is (empty? (synonym.i/list-for-taxon ctx *db* (:taxon/id taxon))))))))

(deftest test-the-same-name-against-two-taxa-is-legal
  ;; WFO itself has one name string that is a synonym of two accepted taxa, so
  ;; there is no unique constraint to violate. Asserting this keeps a
  ;; well-meaning future migration from adding one.
  (tf/testing "two taxa, one synonym name"
    {[::taxon.i/factory :key/a] {:db *db*}
     [::taxon.i/factory :key/b] {:db *db*}}
    (fn [{:keys [a b]}]
      (let [x (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id a)
                                            :synonym-name "Dracaena marginata"})
            y (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id b)
                                            :synonym-name "Dracaena marginata"})]
        (is (not (err.i/error? x)))
        (is (not (err.i/error? y)))
        (jdbc.sql/delete! *db* :taxon_synonym {:id (:synonym/id x)})
        (jdbc.sql/delete! *db* :taxon_synonym {:id (:synonym/id y)})))))

(deftest test-imported-rows-are-listed-and-removable
  ;; `resolve` and `list-for-taxon` include imported rows: they are real garden
  ;; records, and an operator who wants one gone must be able to remove it.
  (tf/testing "source imported"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Bucida buceras"
                                              :source "imported"})]
        (is (= "imported" (:synonym/source row)))
        (is (= ["imported"]
               (mapv :synonym/source
                     (synonym.i/list-for-taxon ctx *db* (:taxon/id taxon)))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-a-floor-database-has-no-local-synonymy
  ;; The table is above the supported floor. A gated read must return empty, not
  ;; throw: the taxon picker calls this on every keystroke.
  (testing "below the gate"
    (is (= [] (synonym.i/list-for-taxon {:schema-version "20260113120000"}
                                        *db* 1)))))
