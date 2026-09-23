(ns sepal.app.routes.media.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
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
            "and the media really has no link, so the nil path was the one exercised")
        (is (re-find #"> Link<" (:body response))
            "the no-link state offers the link action as a labelled button")))))

(deftest test-link-widget-renders-chip-for-linked-media
  (tf/testing "GET on the link widget for linked media renders one removable chip, not a second trash icon"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db* :name "Zzyzxia" :rank :genus}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :title "linked.jpg"
                                     :s3-key "media/linked.jpg"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [user taxon media]}]
      (media.i/link! *db* (:media/id media) (:taxon/id taxon) "taxon")
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (str "/media/" (:media/id media) "/link/"))
            body (:body response)]
        (is (= 200 (:status response)))
        (is (re-find #"Zzyzxia" body) "the chip names the linked resource")
        (is (re-find (re-pattern (str "/taxon/" (:taxon/id taxon) "/")) body)
            "the chip links to the resource")
        (is (re-find #"aria-label=\"Remove link\"" body)
            "the chip carries its own remove control")
        (is (re-find #"aria-label=\"Change link\"" body)
            "a link can be changed, not only removed and re-added")
        (is (not (re-find #"m14\.74 9" body))
            "the trash icon is reserved for deleting the media object itself")))))

(deftest test-link-widget-degrades-on-unknown-resource-type
  (tf/testing "a media_link row with an unrecognised resource type renders rather than throwing"
    ;; Nothing the UI can write today produces one — resource-types is a fixed
    ;; list — but a row written by hand or by a future caller must not 500 the
    ;; widget.
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::media.i/factory :key/media] {:db *db*
                                     :user (ig/ref :key/user)
                                     :title "odd.jpg"
                                     :s3-key "media/odd.jpg"
                                     :s3-bucket "sepal-test-media"}}
    (fn [{:keys [user media]}]
      (db.i/execute-one! *db* {:insert-into [:media_link]
                               :values [{:media-id (:media/id media)
                                         :resource-type "oddity"
                                         :resource-id 1}]})
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (str "/media/" (:media/id media) "/link/"))
            body (:body response)]
        (is (= 200 (:status response)))
        (is (re-find #"oddity" body)
            "the stored type shows as text instead of crashing the widget")))))

