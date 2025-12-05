(ns sepal.material.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.material.interface.activity :as material.activity]
            [sepal.material.interface.spec :as material.spec]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest material-activity
  (tf/testing "material activity tests"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (testing "activity - material created"
            (let [material (mg/generate material.spec/Material)
                  activity (material.activity/create! db
                                                      material.activity/created
                                                      user-id
                                                      material)]
              (is (match? {:activity/id int?
                           :activity/type material.activity/created
                           :activity/data {:material-id (:material/id material)
                                           :material-code (:material/code material)
                                           :accession-id (:material/accession-id material)
                                           :location-id (:material/location-id material)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - material updated"
            (let [material (mg/generate material.spec/Material)
                  activity (material.activity/create! db
                                                      material.activity/updated
                                                      user-id
                                                      material)]
              (is (match? {:activity/id int?
                           :activity/type material.activity/updated
                           :activity/data {:material-id (:material/id material)
                                           :material-code (:material/code material)
                                           :accession-id (:material/accession-id material)
                                           :location-id (:material/location-id material)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - material deleted"
            (let [material (mg/generate material.spec/Material)
                  activity (material.activity/create! db
                                                      material.activity/deleted
                                                      user-id
                                                      material)]
              (is (match? {:activity/id int?
                           :activity/type material.activity/deleted
                           :activity/data {:material-id (:material/id material)
                                           :material-code (:material/code material)
                                           :accession-id (:material/accession-id material)
                                           :location-id (:material/location-id material)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
