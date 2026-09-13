(ns sepal.app.routes.media.delete-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
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
