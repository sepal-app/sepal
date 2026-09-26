(ns sepal.app.routes.media.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.media.interface :as media.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-empty-index-renders-grid-empty-state-and-upload
  (tf/testing "an empty media index renders the grid, the empty state and a second upload trigger"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess "/media/")
            body (:body response)]
        (is (= 200 (:status response)))
        (is (re-find #"id=\"media-list\"" body)
            "the grid exists before the first upload, so an upload has somewhere to prepend")
        (is (re-find #"id=\"media-empty\"" body)
            "the empty state carries an id the uploader removes after the first upload")
        (is (re-find #"id=\"media-empty-upload\"" body)
            "the empty state carries its own upload button")
        (is (re-find #"No media yet" body))))))

(deftest test-index-with-media-hides-empty-state
  (tf/testing "an index with media renders items and no empty state"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :title "one.jpg"}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess "/media/")
            body (:body response)]
        (is (= 200 (:status response)))
        (is (re-find #"id=\"media-list\"" body))
        (is (re-find #"<li" body) "the media item is in the grid")
        (is (not (re-find #"media-empty" body))
            "the empty state stays off the page once there is media")
        (is (re-find #"<p class=\"mt-2 truncate text-sm\" title=\"one.jpg\">one.jpg</p>" body)
            "each tile names its media")
        (is (re-find #"data-size=\"small\"" body)
            "the list starts at the smallest size")
        (is (re-find #"aria-label=\"Thumbnail size\"" body)
            "and offers the size control")))))
