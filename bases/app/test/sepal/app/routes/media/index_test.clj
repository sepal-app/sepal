(ns sepal.app.routes.media.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
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

(defn- titles
  "The tile titles in `body` that start with `prefix`, in page order."
  [body prefix]
  (map second (re-seq (re-pattern (str "title=\"(" prefix "[^\"]*)\"")) body)))

(defn- sort-records
  "Built per test: *db* is bound by the fixture, not when this file loads."
  []
  {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
   [::media.i/factory :key/a] {:db *db* :user (ig/ref :key/user)
                               :title "srt-b.jpg" :size-in-bytes 100}
   [::media.i/factory :key/b] {:db *db* :user (ig/ref :key/user)
                               :title "srt-a.jpg" :size-in-bytes 300}
   [::media.i/factory :key/c] {:db *db* :user (ig/ref :key/user)
                               :title "srt-c.jpg" :size-in-bytes 200}})

(defn- set-created! [media created-at]
  (jdbc.sql/update! *db* :media {:created_at created-at} {:id (:media/id media)}))

(deftest test-each-sort-orders-the-grid
  (tf/testing "newest, oldest, title and largest"
    (sort-records)
    (fn [{:keys [user a b c]}]
      (set-created! a "2020-01-01 00:00:00")
      (set-created! b "2020-01-02 00:00:00")
      (set-created! c "2020-01-03 00:00:00")
      (let [sess (app.test/login (:user/email user) "testpassword123")
            order (fn [sort]
                    (-> (peri/request sess "/media/" :params {"q" "srt" "sort" sort})
                        :response :body (titles "srt-")))]
        (is (= ["srt-c.jpg" "srt-a.jpg" "srt-b.jpg"] (order "newest")))
        (is (= ["srt-b.jpg" "srt-a.jpg" "srt-c.jpg"] (order "oldest")))
        (is (= ["srt-a.jpg" "srt-b.jpg" "srt-c.jpg"] (order "title")))
        (is (= ["srt-a.jpg" "srt-c.jpg" "srt-b.jpg"] (order "largest")))
        (is (= (order "newest") (order "bogus")) "an unknown sort is the default")))))

(deftest test-tied-items-are-not-repeated-across-pages
  (tf/testing "two items uploaded in the same second, one per page"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
     [::media.i/factory :key/a] {:db *db* :user (ig/ref :key/user) :title "tie-1.jpg"}
     [::media.i/factory :key/b] {:db *db* :user (ig/ref :key/user) :title "tie-2.jpg"}}
    (fn [{:keys [user a b]}]
      (set-created! a "2020-01-01 00:00:00")
      (set-created! b "2020-01-01 00:00:00")
      (let [sess (app.test/login (:user/email user) "testpassword123")
            page (fn [n]
                   (-> (peri/request sess "/media/"
                                     :params {"q" "tie" "page-size" "1" "page" (str n) "rows" "1"})
                       :response :body (titles "tie-")))]
        (is (= #{"tie-1.jpg" "tie-2.jpg"} (set (concat (page 1) (page 2)))))
        (is (= 1 (count (page 1))))
        (is (= 1 (count (page 2))))))))

(deftest test-the-next-page-keeps-the-search-and-sort
  (tf/testing "the infinite-scroll URL"
    (sort-records)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (-> (peri/request sess "/media/"
                                   :params {"q" "srt" "sort" "title" "page-size" "1"})
                     :response :body)
            url (second (re-find #"hx-get=\"(/media/[^\"]*)\"" body))]
        (is (some? url) "the grid asks for a next page")
        (is (re-find #"q=srt" url))
        (is (re-find #"sort=title" url))
        (is (re-find #"page=2" url))
        (is (re-find #"rows=1" url))))))

(deftest test-rows-alone-only-when-asked
  (tf/testing "the toolbar's search request gets the whole page"
    (sort-records)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            searched (-> (peri/request sess "/media/"
                                       :params {"q" "srt"}
                                       :headers {"hx-request" "true"})
                         :response :body)
            rows (-> (peri/request sess "/media/"
                                   :params {"q" "srt" "rows" "1"}
                                   :headers {"hx-request" "true"})
                     :response :body)]
        (is (re-find #"id=\"list-container\"" searched)
            "an htmx request without rows is the search form's, and swaps the container")
        (is (re-find #"name=\"sort\"" searched))
        (is (re-find #"<li" rows))
        (is (not (re-find #"spl-toolbar" rows)) "rows alone carry no toolbar")))))

(deftest test-a-search-that-matches-nothing
  (tf/testing "says so, rather than offering a first upload"
    (sort-records)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (-> (peri/request sess "/media/" :params {"q" "zzznomatch"})
                     :response :body)]
        (is (re-find #"Nothing matched" body))
        (is (not (re-find #"id=\"media-empty\"" body)))))))

(deftest test-the-uploader-sits-outside-the-list
  (tf/testing "the shared uploader mounts once, where a search can't replace it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (with-redefs [media.ui/uploads-enabled? (constantly true)]
                   (-> (peri/request sess "/media/") :response :body))
            uploader-at (.indexOf ^String body "x-media-uploader")
            container-at (.indexOf ^String body "id=\"list-container\"")]
        (is (re-find #"uploadedUrl" body) "the uploader is media.ui/uploader's")
        (is (= 1 (count (re-seq #"x-media-uploader" body))))
        (is (< -1 uploader-at container-at) "and comes before the list container")))))

(deftest test-the-count-follows-the-scroll
  (tf/testing "each page of tiles updates the toolbar's count out of band"
    (sort-records)
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            rows (-> (peri/request sess "/media/"
                                   :params {"q" "srt" "page-size" "1" "page" "2" "rows" "1"})
                     :response :body)]
        (is (re-find #"hx-swap-oob=\"true\"" rows))
        (is (re-find #"2 of 3" rows))))))
