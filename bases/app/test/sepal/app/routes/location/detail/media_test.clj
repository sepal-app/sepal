(ns sepal.app.routes.location.detail.media-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

(deftest test-a-location-shows-its-materials-media-only-when-asked
  (tf/testing "the location's own photo shows by default; a photo of material
  stored there joins it when the toggle is on, marked with the material"
    {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/loc] {:db *db*}
     [::material.i/factory :key/mat] {:db *db*
                                      :accession (ig/ref :key/acc)
                                      :location (ig/ref :key/loc)}
     [::media.i/factory :key/on-loc] {:db *db* :user (ig/ref :key/user)}
     [::media.i/factory :key/on-mat] {:db *db* :user (ig/ref :key/user)}}
    (fn [{:keys [user acc loc mat on-loc on-mat]}]
      (let [sess (app.test/login (:user/email user) password)
            url (format "/location/%s/media/" (:location/id loc))
            tile (fn [q media]
                   (-> (peri/request sess (str url q)) :response :body Jsoup/parse
                       (.selectFirst (format "#media-list > li:has(a[href=/media/%s/])"
                                             (:media/id media)))))]
        (try
          (media.i/link! *db* (:media/id on-loc) (:location/id loc) :location)
          (media.i/link! *db* (:media/id on-mat) (:material/id mat) :material)
          (is (some? (tile "" on-loc)))
          (is (nil? (tile "" on-mat)))
          (let [mat-tile (tile "?scope=below" on-mat)]
            (is (some? mat-tile))
            (is (str/includes? (.text mat-tile) (str "via " (:accession/code acc)))))
          (finally
            (media.i/unlink! *db* (:media/id on-loc))
            (media.i/unlink! *db* (:media/id on-mat))))))))
