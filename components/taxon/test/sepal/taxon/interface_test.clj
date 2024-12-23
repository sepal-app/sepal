(ns sepal.taxon.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.spec :as taxon.spec]))

(use-fixtures :once default-system-fixture)

(deftest test-specs
  (testing "CreateTaxon - encode/store"
    (let [data {:taxon/id 123
                :taxon/rank :genus}]
      (is (match? (store.i/encode taxon.spec/CreateTaxon data)
                  {:id 1234
                   :rank "genus"}))))

  (testing "CreateTaxon - encode/db"
    (let [data {:taxon/id 123
                :taxon/rank :genus}]
      (is (match? (store.i/encode taxon.spec/CreateTaxon data)
                  {:id 1234
                   :rank "genus"})))))

(deftest test-create
  (tf/testing "create!"
    (let [db *db*
          taxon-name (mg/generate [:string {:min 1}])
          result (taxon.i/create! db {:name taxon-name
                                      :rank :genus})]
      (is (not (err.i/error? result)) (err.i/data result))
      (is (match? {:taxon/id pos-int?
                   :taxon/name taxon-name
                   :taxon/rank :genus
                   :taxon/author nil}
                  result))
      (jdbc.sql/delete! db :taxon {:id (:taxon/id result)}))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update! - org taxon"
      {[::taxon.i/factory :key/taxon]
       {:db db}}
      (fn [{:keys [taxon]}]
        (let [taxon-name (mg/generate [:string {:min 1}])
              taxon-rank :genus
              result (taxon.i/update! db
                                      (:taxon/id taxon)
                                      {:name taxon-name
                                       :rank taxon-rank})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (match? {:taxon/id pos-int?
                       :taxon/name taxon-name
                       :taxon/rank :genus
                       :taxon/author (:taxon/author taxon)}
                      result)))))))
