(ns sepal.app.routes.media.delete-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.routes.media.detail :as media.detail]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-deleting-media-removes-its-link
  (tf/testing "media.i/delete!"
    {[::user.i/factory :key/user] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::media.i/factory :key/media] {:db *db* :user (ig/ref :key/user)}}
    (fn [{:keys [taxon media]}]
      (media.i/link! *db* (:media/id media) (:taxon/id taxon) :taxon)
      (is (some? (media.i/get-link *db* (:media/id media))))
      (media.i/delete! *db* (:media/id media))
      (is (empty? (db.i/execute! *db* {:select [:id]
                                       :from [:media_link]
                                       :where [:= :media_id (:media/id media)]}))
          "A media_link row must not outlive its media row"))))

(deftest test-deleting-media-is-in-the-activity-log
  (tf/testing "media.detail/delete!"
    {[::user.i/factory :key/user] {:db *db*}
     [::media.i/factory :key/media] {:db *db* :user (ig/ref :key/user)}}
    (fn [{:keys [user media]}]
      (try
        ;; No S3 client: the object delete fails and is logged, and the row
        ;; and its event are already committed.
        (media.detail/delete! *db* nil media (:user/id user))
        (is (nil? (media.i/get-by-id *db* (:media/id media))))
        (let [[event] (jdbc.sql/find-by-keys *db* :activity {:type "media/deleted"
                                                             :resource_id (:media/id media)})]
          (is (some? event) "the delete is recorded")
          (is (= "media" (:activity/resource-type event))))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

