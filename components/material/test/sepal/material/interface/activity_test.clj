(ns sepal.material.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.activity.interface :as activity.i]
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
                           :activity/resource-type :material
                           :activity/resource-id (:material/id material)
                           :activity/data {:material-code (:material/code material)
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
                           :activity/resource-type :material
                           :activity/resource-id (:material/id material)
                           :activity/data {:material-code (:material/code material)
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
                           :activity/resource-type :material
                           :activity/resource-id (:material/id material)
                           :activity/data {:material-code (:material/code material)
                                           :accession-id (:material/accession-id material)
                                           :location-id (:material/location-id material)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          ;; A material event used to surface under its accession and its
          ;; location too, because the query matched any <type>-id key in the
          ;; payload and this payload carries both. Nobody designed that, and
          ;; two columns cannot name three subjects, so it is gone on purpose.
          ;; :accession-id and :location-id are still in the payload as
          ;; context; they are no longer a query path.
          (testing "the event does not surface under its accession or location"
            (let [material (mg/generate material.spec/Material)]
              (material.activity/create! db material.activity/created
                                         user-id material)
              (is (empty? (activity.i/get-by-resource
                            db
                            :resource-type :accession
                            :resource-id (:material/accession-id material))))
              (is (empty? (activity.i/get-by-resource
                            db
                            :resource-type :location
                            :resource-id (:material/location-id material))))
              (testing "but it is on the material's own history"
                (is (some #(= material.activity/created (:activity/type %))
                          (activity.i/get-by-resource
                            db
                            :resource-type :material
                            :resource-id (:material/id material)))))))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
