(ns sepal.location.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.location.interface.activity :as location.activity]
            [sepal.location.interface.spec :as location.spec]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest location-activity
  (tf/testing "location activity tests"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (testing "activity - location created"
            (let [location (mg/generate location.spec/Location)
                  activity (location.activity/create! db
                                                      location.activity/created
                                                      user-id
                                                      location)]
              (is (match? {:activity/id int?
                           :activity/type location.activity/created
                           :activity/data {:location-id (:location/id location)
                                           :location-name (:location/name location)
                                           :location-code (:location/code location)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - location updated"
            (let [location (mg/generate location.spec/Location)
                  activity (location.activity/create! db
                                                      location.activity/updated
                                                      user-id
                                                      location)]
              (is (match? {:activity/id int?
                           :activity/type location.activity/updated
                           :activity/data {:location-id (:location/id location)
                                           :location-name (:location/name location)
                                           :location-code (:location/code location)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - location deleted"
            (let [location (mg/generate location.spec/Location)
                  activity (location.activity/create! db
                                                      location.activity/deleted
                                                      user-id
                                                      location)]
              (is (match? {:activity/id int?
                           :activity/type location.activity/deleted
                           :activity/data {:location-id (:location/id location)
                                           :location-name (:location/name location)
                                           :location-code (:location/code location)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
