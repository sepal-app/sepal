(ns sepal.propagation.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.propagation.interface.activity :as propagation.activity]
            [sepal.propagation.interface.spec :as propagation.spec]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest propagation-activity
  (tf/testing "propagation activity tests"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (testing "activity - propagation created"
            (let [propagation (mg/generate propagation.spec/Propagation)
                  activity (propagation.activity/create! db
                                                         propagation.activity/created
                                                         user-id
                                                         propagation)]
              (is (match? {:activity/id int?
                           :activity/type propagation.activity/created
                           :activity/resource-type :propagation
                           :activity/resource-id (:propagation/id propagation)
                           :activity/data {:propagation-type (:propagation/type propagation)
                                           :parent-accession-id (:propagation/parent-accession-id propagation)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - propagation updated"
            (let [propagation (mg/generate propagation.spec/Propagation)
                  activity (propagation.activity/create! db
                                                         propagation.activity/updated
                                                         user-id
                                                         propagation)]
              (is (match? {:activity/id int?
                           :activity/type propagation.activity/updated
                           :activity/resource-type :propagation
                           :activity/resource-id (:propagation/id propagation)
                           :activity/data {:propagation-type (:propagation/type propagation)
                                           :parent-accession-id (:propagation/parent-accession-id propagation)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - propagation deleted"
            (let [propagation (mg/generate propagation.spec/Propagation)
                  activity (propagation.activity/create! db
                                                         propagation.activity/deleted
                                                         user-id
                                                         propagation)]
              (is (match? {:activity/id int?
                           :activity/type propagation.activity/deleted
                           :activity/resource-type :propagation
                           :activity/resource-id (:propagation/id propagation)
                           :activity/data {:propagation-type (:propagation/type propagation)
                                           :parent-accession-id (:propagation/parent-accession-id propagation)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
