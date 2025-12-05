(ns sepal.taxon.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.taxon.interface.spec :as taxon.spec]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest taxon-activity
  (tf/testing "taxon activity tests"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (testing "activity - taxon created"
            (let [taxon (mg/generate taxon.spec/Taxon)
                  activity (taxon.activity/create! db
                                                   taxon.activity/created
                                                   user-id
                                                   taxon)]
              (is (match? {:activity/id int?
                           :activity/type taxon.activity/created
                           :activity/data {:taxon-id (:taxon/id taxon)
                                           :taxon-name (:taxon/name taxon)
                                           :taxon-author (:taxon/author taxon)
                                           :taxon-rank (:taxon/rank taxon)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - taxon updated"
            (let [taxon (mg/generate taxon.spec/Taxon)
                  activity (taxon.activity/create! db
                                                   taxon.activity/updated
                                                   user-id
                                                   taxon)]
              (is (match? {:activity/id int?
                           :activity/type taxon.activity/updated
                           :activity/data {:taxon-id (:taxon/id taxon)
                                           :taxon-name (:taxon/name taxon)
                                           :taxon-author (:taxon/author taxon)
                                           :taxon-rank (:taxon/rank taxon)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - taxon deleted"
            (let [taxon (mg/generate taxon.spec/Taxon)
                  activity (taxon.activity/create! db
                                                   taxon.activity/deleted
                                                   user-id
                                                   taxon)]
              (is (match? {:activity/id int?
                           :activity/type taxon.activity/deleted
                           :activity/data {:taxon-id (:taxon/id taxon)
                                           :taxon-name (:taxon/name taxon)
                                           :taxon-author (:taxon/author taxon)
                                           :taxon-rank (:taxon/rank taxon)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
