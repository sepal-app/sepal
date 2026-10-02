(ns sepal.app.routes.media.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.app.ui.media :as media.ui]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-empty-index-renders-grid-empty-state-and-upload
  (tf/testing "an empty media index renders the grid, the empty state and a second upload trigger"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (with-redefs [media.ui/uploads-enabled? (constantly true)]
                                 (peri/request sess "/media/"))
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

(deftest test-no-upload-controls-when-uploads-are-off
  ;; Without S3 credentials the app builds no presigner, so an upload can't be
  ;; signed. Offering one anyway ended in a 500.
  (tf/testing "with no presigner, no page offers an upload"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")]
        (doseq [url ["/media/" (format "/material/%s/media/" (:material/id material))]]
          (let [body (:body (:response (peri/request sess url)))]
            (is (not (re-find #"id=\"upload-button\"" body)) url)
            (is (not (re-find #"id=\"media-empty-upload\"" body)) url)
            (is (not (re-find #"x-media-uploader" body)) url)
            (is (not (re-find #"Drag images here" body)) url)))))))
