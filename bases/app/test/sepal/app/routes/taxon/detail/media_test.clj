(ns sepal.app.routes.taxon.detail.media-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(deftest test-the-media-tab-widens-to-media-linked-below
  (tf/testing "a photo of an accession shows on its taxon's tab only when the
  scope widens, and says where it is linked"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db* :name "Viaia rosea"}
     [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}
     [::media.i/factory :key/on-taxon] {:db *db* :user (ig/ref :key/user)}
     [::media.i/factory :key/on-acc] {:db *db* :user (ig/ref :key/user)}}
    (fn [{:keys [user taxon acc on-taxon on-acc]}]
      (let [sess (app.test/login (:user/email user) password)
            url (format "/taxon/%s/media/" (:taxon/id taxon))
            tiles (fn [q]
                    (-> (peri/request sess (str url q)) :response :body Jsoup/parse
                        (.select "#media-list > li")))
            tile-for (fn [tiles media]
                       (first (filter #(.selectFirst % (format "a[href=/media/%s/]" (:media/id media)))
                                      tiles)))]
        (try
          (media.i/link! *db* (:media/id on-taxon) (:taxon/id taxon) :taxon)
          (media.i/link! *db* (:media/id on-acc) (:accession/id acc) :accession)
          (is (some? (-> (peri/request sess url) :response :body Jsoup/parse
                         (.selectFirst ".spl-record-page--wide")))
              "a Media tab is not a form, so it takes the wide column")
          (let [direct (tiles "")]
            (is (some? (tile-for direct on-taxon)))
            (is (nil? (tile-for direct on-acc)) "direct leaves out the accession's photo"))
          (let [below (tiles "?scope=below")
                acc-tile (tile-for below on-acc)]
            (is (some? acc-tile))
            (is (str/includes? (.text acc-tile) (str "via " (:accession/code acc))))
            (is (not (str/includes? (.text (tile-for below on-taxon)) "via"))
                "a photo linked to the taxon itself carries no note"))
          (finally
            (media.i/unlink! *db* (:media/id on-taxon))
            (media.i/unlink! *db* (:media/id on-acc))))))))
