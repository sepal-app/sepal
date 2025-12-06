(ns sepal.media.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.media.interface.activity :as media.activity]
            [sepal.media.interface.spec :as media.spec]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest media-activity
  (tf/testing "media activity tests"
    {[::user.i/factory :key/user] {:db *db*}}
    (fn [{:keys [user]}]
      (let [db *db*
            user-id (:user/id user)]
        (try
          (testing "activity - media created"
            (let [media (mg/generate media.spec/Media)
                  activity (media.activity/create! db
                                                   media.activity/created
                                                   user-id
                                                   media)]
              (is (match? {:activity/id int?
                           :activity/type media.activity/created
                           :activity/data {:media-id (:media/id media)
                                           :s3-key (:media/s3-key media)
                                           :media-type (:media/media-type media)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          (testing "activity - media deleted"
            (let [media (mg/generate media.spec/Media)
                  activity (media.activity/create! db
                                                   media.activity/deleted
                                                   user-id
                                                   media)]
              (is (match? {:activity/id int?
                           :activity/type media.activity/deleted
                           :activity/data {:media-id (:media/id media)
                                           :s3-key (:media/s3-key media)
                                           :media-type (:media/media-type media)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
