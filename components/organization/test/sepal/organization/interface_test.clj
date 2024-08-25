(ns sepal.organization.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.error :as me]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]))

(use-fixtures :once default-system-fixture)

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "org.i/get-by-id"
      {[::org.i/factory :key/org]
       {:db *db*}}
      (fn [{:keys [org]}]
        (is (match? org
                    (org.i/get-by-id db (:organization/id org))))))))

(deftest test-create!
  (let [db *db*]
    (tf/testing "org.i/create!"
      (let [org (org.i/create! db {:name (mg/generate [:string {:min 1}])})]
        (is (match? {:organization/id pos-int?
                     :organization/name string?
                     :organization/abbreviation nil?
                     :organization/short-name nil?}
                    org))
        (jdbc.sql/delete! db :public.organization {:id (:organization/id org)})))

    (tf/testing "org.i/create! - with id"
      (let [id (mg/generate pos-int?)
            org (org.i/create! db {:name (mg/generate [:string {:min 1}])
                                   :id id})]
        (is (match? {:organization/id id
                     :organization/name string?
                     :organization/abbreviation nil?
                     :organization/short-name nil?}
                    org))
        (jdbc.sql/delete! db :public.organization {:id (:organization/id org)})))

    (tf/testing "org.i/create! - with all"
      (let [data {:id (mg/generate pos-int?)
                  :name (mg/generate [:string {:min 1}])
                  :abbreviation (mg/generate [:string {:min 1}])
                  :short-name (mg/generate [:string {:min 1}])}
            org (org.i/create! db data)]
        (is (match? {:organization/id (:id data)
                     :organization/name (:name data)
                     :organization/abbreviation (:abbreviation data)
                     :organization/short-name (:short-name data)}
                    org))
        (jdbc.sql/delete! db :public.organization {:id (:organization/id org)})))

    (tf/testing "org.i/create! - with nil vals"
      (let [data {:id (mg/generate pos-int?)
                  :name (mg/generate [:string {:min 1}])
                  :abbreviation nil
                  :short-name nil}
            org (org.i/create! db data)]
        (is (match? {:organization/id (:id data)
                     :organization/name (:name data)
                     :organization/abbreviation nil?
                     :organization/short-name nil?}
                    org))
        (jdbc.sql/delete! db :public.organization {:id (:organization/id org)})))

    (tf/testing "org.i/create! - error: invalid id"
      (let [id (mg/generate neg-int?)
            result (org.i/create! db {:name (mg/generate [:string {:min 1}])
                                      :id id})
            errors (-> result error.i/data :explain me/humanize)]

        (is (match? {:id ["should be a positive int"]}
                    errors))))

    (tf/testing "org.i/create! - error: id=0"
      (let [id 0
            result (org.i/create! db {:name (mg/generate [:string {:min 1}])
                                      :id id})
            errors (-> result error.i/data :explain me/humanize)]
        (is (match? {:id ["should be a positive int"]}
                    errors))))))
