(ns sepal.organization.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]
            [sepal.organization.interface.activity :as org.activity]
            [sepal.organization.interface.spec :as org.spec]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "org.activity/create!"
      {[::org.i/factory :key/org] {:db *db*
                                   :abbreviation (mg/generate org.spec/abbreviation)
                                   :short-name (mg/generate org.spec/short-name)}
       [::user.i/factory :key/user] {:db *db*
                                     :org (ig/ref :key/org)}
       [::org.i/organization-user-factory :key/org-user] {:db *db*
                                                          :org (ig/ref :key/org)
                                                          :user (ig/ref :key/user)
                                                          :role "owner"}}
      (fn [{:keys [org user]}]
        (let [result (org.activity/create! db org.activity/created (:user/id user) org)]
          (is (not (error.i/error? result)))
          (is (match? {:activity/type :organization/created
                       :activity/created-at inst?
                       :activity/created-by (:user/id user)
                       :activity/organization-id (:organization/id org)
                       :activity/data {:organization-id (:organization/id org)
                                       :organization-name (:organization/name org)
                                       :organization-short-name (:organization/short-name org)
                                       :organization-abbreviation (:organization/abbreviation org)}}
                      result))
          (jdbc.sql/delete! db :activity {:id (:activity/id result)}))))))
