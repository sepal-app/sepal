(ns sepal.media.interface.activity-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.activity.interface :as activity.i]
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
                           :activity/resource-type :media
                           :activity/resource-id (:media/id media)
                           :activity/data {:s3-key (:media/s3-key media)
                                           :media-type (:media/media-type media)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))

          ;; Media carried no <type>-id key, so the old query found its events
          ;; under nothing. They were written and unreadable.
          (testing "a media event is findable by its subject"
            (let [media (mg/generate media.spec/Media)]
              (media.activity/create! db media.activity/created user-id media)
              (is (some #(= media.activity/created (:activity/type %))
                        (activity.i/get-by-resource db
                                                    :resource-type :media
                                                    :resource-id (:media/id media))))))

          (testing "activity - media deleted"
            (let [media (mg/generate media.spec/Media)
                  activity (media.activity/create! db
                                                   media.activity/deleted
                                                   user-id
                                                   media)]
              (is (match? {:activity/id int?
                           :activity/type media.activity/deleted
                           :activity/resource-type :media
                           :activity/resource-id (:media/id media)
                           :activity/data {:s3-key (:media/s3-key media)
                                           :media-type (:media/media-type media)}
                           :activity/created-by user-id
                           :activity/created-at inst?}
                          activity))))
          (finally
            ;; Clean up activity records before user fixture cleanup
            (jdbc.sql/delete! db :activity {:created_by user-id})))))))
