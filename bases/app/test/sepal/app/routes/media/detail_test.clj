(ns sepal.app.routes.media.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.media.interface :as media.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-delete-refuses-foreign-instance-key
  (tf/testing "DELETE on media outside this instance's prefix is refused and leaves the row intact"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     ;; A non-nil title avoids an unrelated NPE
                                     ;; in download-url's filename encoding.
                                     :title "deadbeef.jpg"
                                     :s3-key "elsewhere/deadbeef.jpg"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [user media]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess "/settings/profile")
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess (str "/media/" (:media/id media) "/")
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 404 (:status response))
            "media outside this instance's prefix is no media of ours, not a server error")
        (is (some? (media.i/get-by-id *db* (:media/id media)))
            "the media row must still exist after a refused delete")))))

(deftest test-link-widget-renders-for-media-with-no-link
  (tf/testing "GET on the link widget for media with no link offers the link control rather than 500ing"
    ;; Media uploaded from /media/ carries no linkResourceType/linkResourceId, so
    ;; it has no media_link row. That is the ordinary case, not an edge.
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :title "unlinked.jpg"
                                     :s3-key "media/unlinked.jpg"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [user media]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (str "/media/" (:media/id media) "/link/"))]
        (is (= 200 (:status response))
            "no link is a state the widget renders, not a server error")
        (is (nil? (media.i/get-link *db* (:media/id media)))
            "and the media really has no link, so the nil path was the one exercised")))))

