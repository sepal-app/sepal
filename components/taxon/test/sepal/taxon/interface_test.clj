(ns sepal.taxon.interface-test
  (:require [clojure.test :as test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as err.i]
            [sepal.organization.interface :as org.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.spec :as taxon.spec]))

(use-fixtures :once default-system-fixture)

(deftest test-specs
  (testing "CreateTaxon - encode/db"
    (let [data {:taxon/id 123
                :taxon/rank :genus}]
      (is (match? (db.i/encode  taxon.spec/CreateTaxon data)
                  {:id 1234
                   :rank "genus"}))))

  (testing "CreateTaxon - encode/db"
    (let [data {:taxon/id 123
                :taxon/rank :genus}]
      (is (match? (db.i/encode  taxon.spec/CreateTaxon data)
                  {:id 1234
                   :rank "genus"})))))

(deftest test-create
  (tf/testing "create!"
    {[::org.i/factory :key/org]
     {:db *db*}}
    (fn [{:keys [org]}]
      (let [db *db*
            result (taxon.i/create! db {:name "test"
                                        :rank :genus
                                        :organization-id (:organization/id org)})]
        (is (not (err.i/error? result)) (err.i/data result))
        (is (match? {:taxon/id int
                     :taxon/name "test"
                     :taxon/organization-id (:organization/id org)
                     :taxon/rank :genus
                     :taxon/wfo-plantlist-name-id nil
                     :taxon/author ""}
                    result))
        (jdbc.sql/delete! db :taxon {:id (:taxon/id result)})))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update! - org taxon"
      {[::org.i/factory :key/org]
       {:db db}
       [::taxon.i/factory :key/taxon]
       {:db db
        :organization (ig/ref :key/org)}}
      (fn [{:keys [org taxon]}]
        (let [result (taxon.i/update! db
                                      (:taxon/id taxon)
                                      {:name "test"
                                       :rank :genus})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (match? {:taxon/id int
                       :taxon/name "test"
                       :taxon/organization-id (:organization/id org)
                       :taxon/rank :genus
                       :taxon/wfo-plantlist-name-id nil
                       :taxon/author ""}
                      result)))))

    (tf/testing "update! - wfo taxon"
      {[::org.i/factory :key/org]
       {:db db}}
      (fn [{:keys [org]}]
        (let [wfo-id "wfo-1234"
              result (taxon.i/update! db
                                      wfo-id
                                      {:name "test"
                                       :rank :genus
                                       :organization-id (:organization/id org)})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (match? {:taxon/id int
                       :taxon/name "test"
                       :taxon/organization-id (:organization/id org)
                       :taxon/rank :genus
                       :taxon/wfo-plantlist-name-id wfo-id
                       :taxon/author ""}
                      result))
          (jdbc.sql/delete! db :taxon {:id (:taxon/id result)}))))))
