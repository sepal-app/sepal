(ns sepal.activity.interface-test
  (:require [clojure.test :as test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.user.interface :as user.i])
  (:import [java.time Instant]))

(use-fixtures :once default-system-fixture)

;; Define a test-specific activity type and data schema
(def test-activity-type :test/activity)

(def TestActivityData
  [:map
   [:test-id pos-int?]
   [:test-name :string]])

(defmethod activity.i/data-schema test-activity-type [_]
  TestActivityData)

(deftest test-data-schema-multimethod
  (testing "data-schema returns registered schema"
    (is (= TestActivityData (activity.i/data-schema test-activity-type))))

  (testing "data-schema throws for unregistered type"
    (is (thrown? IllegalArgumentException
                 (activity.i/data-schema :unregistered/type)))))

(deftest test-create-activity
  (tf/testing "create! inserts activity record"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (testing "creates activity with correct structure"
            (let [activity (activity.i/create! db
                                               {:type test-activity-type
                                                :created-at (Instant/now)
                                                :created-by user-id
                                                :data {:test-id 123
                                                       :test-name "test"}})]
              (is (match? {:activity/id pos-int?
                           :activity/type test-activity-type
                           :activity/data {:test-id 123
                                           :test-name "test"}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))

(deftest test-create-activity-with-different-types
  (tf/testing "create! works with different registered activity types"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          ;; Test with the media activity type (registered elsewhere)
          (testing "works with :media/created type"
            (let [activity (activity.i/create! db
                                               {:type :media/created
                                                :created-at (Instant/now)
                                                :created-by user-id
                                                :data {:media-id 1
                                                       :s3-key "test.jpg"
                                                       :media-type "image/jpeg"}})]
              (is (match? {:activity/id pos-int?
                           :activity/type :media/created}
                          activity))))

          (testing "works with :media/deleted type"
            (let [activity (activity.i/create! db
                                               {:type :media/deleted
                                                :created-at (Instant/now)
                                                :created-by user-id
                                                :data {:media-id 2
                                                       :s3-key "deleted.jpg"
                                                       :media-type "image/png"}})]
              (is (match? {:activity/id pos-int?
                           :activity/type :media/deleted}
                          activity))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))

(deftest test-create-activity-timestamps
  (tf/testing "create! handles timestamps correctly"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (let [before (Instant/now)
                activity (activity.i/create! db
                                             {:type test-activity-type
                                              :created-at (Instant/now)
                                              :created-by user-id
                                              :data {:test-id 456
                                                     :test-name "timestamp-test"}})
                after (Instant/now)]
            (testing "created-at is an Instant"
              (is (instance? Instant (:activity/created-at activity))))

            (testing "created-at is within expected time range"
              (is (not (.isBefore (:activity/created-at activity) before)))
              (is (not (.isAfter (:activity/created-at activity) after)))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))

(deftest test-create-multiple-activities
  (tf/testing "create! generates unique IDs for each activity"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (let [activities (doall
                             (for [i (range 1 6)]
                               (activity.i/create! db
                                                   {:type test-activity-type
                                                    :created-at (Instant/now)
                                                    :created-by user-id
                                                    :data {:test-id i
                                                           :test-name (str "test-" i)}})))]
            (testing "all activities have unique IDs"
              (let [ids (map :activity/id activities)]
                (is (= 5 (count ids)))
                (is (= 5 (count (set ids))))))

            (testing "all activities have the correct type"
              (is (every? #(= test-activity-type (:activity/type %)) activities))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))

;; Define a taxon activity type for testing resource queries
(def test-taxon-activity-type :test-taxon/activity)

(def TestTaxonActivityData
  [:map
   [:taxon-id pos-int?]])

(defmethod activity.i/data-schema test-taxon-activity-type [_]
  TestTaxonActivityData)

(deftest test-get-by-resource
  (tf/testing "get-by-resource returns activities for a specific resource"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)
            taxon-id 12345]
        (try
          ;; Create activities for the test taxon
          (dotimes [_ 3]
            (activity.i/create! db
                                {:type test-taxon-activity-type
                                 :created-at (Instant/now)
                                 :created-by user-id
                                 :data {:taxon-id taxon-id}}))
          ;; Create activity for a different taxon
          (activity.i/create! db
                              {:type test-taxon-activity-type
                               :created-at (Instant/now)
                               :created-by user-id
                               :data {:taxon-id 99999}})

          (testing "returns only activities for the specified resource"
            (let [activities (activity.i/get-by-resource db
                                                         :resource-type :taxon
                                                         :resource-id taxon-id)]
              (is (= 3 (count activities)))
              (is (every? #(= taxon-id (get-in % [:activity/data :taxon-id])) activities))))

          (testing "includes user info in results"
            (let [activities (activity.i/get-by-resource db
                                                         :resource-type :taxon
                                                         :resource-id taxon-id)]
              (is (every? #(contains? % :activity/user) activities))
              (is (every? #(= user-id (get-in % [:activity/user :user/id])) activities))))

          (testing "respects limit parameter"
            (let [activities (activity.i/get-by-resource db
                                                         :resource-type :taxon
                                                         :resource-id taxon-id
                                                         :limit 2)]
              (is (= 2 (count activities)))))

          (testing "returns empty for non-existent resource"
            (let [activities (activity.i/get-by-resource db
                                                         :resource-type :taxon
                                                         :resource-id 0)]
              (is (empty? activities))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))

(deftest test-count-by-resource
  (tf/testing "count-by-resource returns correct count for a resource"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)
            taxon-id 54321]
        (try
          ;; Create activities for the test taxon
          (dotimes [_ 5]
            (activity.i/create! db
                                {:type test-taxon-activity-type
                                 :created-at (Instant/now)
                                 :created-by user-id
                                 :data {:taxon-id taxon-id}}))

          (testing "returns correct count"
            (is (= 5 (activity.i/count-by-resource db
                                                   :resource-type :taxon
                                                   :resource-id taxon-id))))

          (testing "returns 0 for non-existent resource"
            (is (= 0 (activity.i/count-by-resource db
                                                   :resource-type :taxon
                                                   :resource-id 0))))
          (finally
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
