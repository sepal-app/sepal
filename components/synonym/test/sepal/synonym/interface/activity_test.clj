(ns sepal.synonym.interface.activity-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.synonym.interface.activity :as synonym.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-created-and-deleted
  (tf/testing "both event types validate and round-trip"
    {[::user.i/factory :key/user] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [user-id (:user/id user)
            data {:synonym/id 1
                  :synonym/taxon-id (:taxon/id taxon)
                  :synonym/synonym-name "Encyclia cochleata"}]
        (try
          (doseq [type [synonym.activity/created synonym.activity/deleted]]
            (is (match? {:activity/type type
                         :activity/created-by user-id
                         :activity/data {:synonym-id 1
                                         :taxon-id (:taxon/id taxon)
                                         :synonym-name "Encyclia cochleata"}
                         :activity/created-at inst?}
                        (synonym.activity/create! *db* type user-id data))))
          (finally
            (jdbc.sql/delete! *db* :activity {:created_by user-id})))))))
