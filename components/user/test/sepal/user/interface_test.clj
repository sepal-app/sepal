(ns sepal.user.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [matcher-combinators.test :refer [match?]]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "user.i/get-by-id"
      {[::org.i/factory :key/org] {:db *db*}
       [::user.i/factory :key/user] {:db *db*
                                     :org (ig/ref :key/org)}
       [::org.i/organization-user-factory :key/org-user] {:db *db*
                                                          :org (ig/ref :key/org)
                                                          :user (ig/ref :key/user)
                                                          :role "owner"}}
      (fn [{:keys [org]}]
        (is (match? org
                    (org.i/get-by-id db (:organization/id org))))))))
