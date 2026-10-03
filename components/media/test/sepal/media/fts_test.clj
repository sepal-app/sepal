(ns sepal.media.fts-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- indexed
  "What media_fts holds for one media item, or nil when it has no row."
  [media]
  (jdbc/execute-one! *db*
                     ["select title, description, linked from media_fts where rowid = ?"
                      (:media/id media)]
                     {:builder-fn rs/as-unqualified-maps}))

(defn- linked [media]
  (:linked (indexed media)))

(defn- records
  "Built per test: *db* is bound by the fixture, not when this file loads."
  []
  {[::user.i/factory :key/user] {:db *db*}
   [::taxon.i/factory :key/taxon] {:db *db* :name "Quercus fts-test"}
   [::location.i/factory :key/location] {:db *db* :data {:code "FTS-B12" :name "Rose garden"}}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                           :data {:code "2099.7401"}}
   [::accession.i/factory :key/other-accession] {:db *db* :taxon (ig/ref :key/taxon)
                                                 :data {:code "2099.7403"}}
   [::material.i/factory :key/material] {:db *db* :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)
                                         :data {:code "1"}}
   [::media.i/factory :key/media] {:db *db* :user (ig/ref :key/user)
                                   :title "oak.jpg" :description "a big tree"}
   [::media.i/factory :key/other-media] {:db *db* :user (ig/ref :key/user)
                                         :title "maple.jpg"}})

(deftest test-media-rows-are-indexed
  (tf/testing "uploading, editing and deleting media"
    (records)
    (fn [{:keys [media]}]
      (is (= {:title "oak.jpg" :description "a big tree" :linked nil} (indexed media)))
      (media.i/update! *db* (:media/id media) {:title "renamed.jpg"})
      (is (= "renamed.jpg" (:title (indexed media))))
      (media.i/delete! *db* (:media/id media))
      (is (nil? (indexed media))))))

(deftest test-linking-sets-the-label
  (tf/testing "each kind of link, a relink and an unlink"
    (records)
    (fn [{:keys [media accession material taxon location]}]
      (let [id (:media/id media)]
        (media.i/link! *db* id (:accession/id accession) "accession")
        (is (= "2099.7401" (linked media)) "accession")
        ;; link! upserts, so each of these is an UPDATE of media_link.
        (media.i/link! *db* id (:material/id material) "material")
        (is (= "2099.7401 1" (linked media)) "material")
        (media.i/link! *db* id (:taxon/id taxon) "taxon")
        (is (= "Quercus fts-test" (linked media)) "taxon")
        (media.i/link! *db* id (:location/id location) "location")
        (is (= "FTS-B12 Rose garden" (linked media)) "location")
        (media.i/unlink! *db* id)
        (is (nil? (linked media)) "unlinked")))))

(deftest test-renaming-a-linked-record-updates-the-label
  (tf/testing "a rename reaches the media linked to the record"
    (records)
    (fn [{:keys [media other-media accession other-accession material taxon location]}]
      (media.i/link! *db* (:media/id media) (:accession/id accession) "accession")
      (media.i/link! *db* (:media/id other-media) (:material/id material) "material")
      (testing "accession code, on its own media and its material's"
        (jdbc.sql/update! *db* :accession {:code "2099.7402"} {:id (:accession/id accession)})
        (is (= "2099.7402" (linked media)))
        (is (= "2099.7402 1" (linked other-media))))
      (testing "material code"
        (jdbc.sql/update! *db* :material {:code "2"} {:id (:material/id material)})
        (is (= "2099.7402 2" (linked other-media))))
      (testing "material moved to another accession"
        (jdbc.sql/update! *db* :material {:accession_id (:accession/id other-accession)}
                          {:id (:material/id material)})
        (is (= "2099.7403 2" (linked other-media)))
        ;; Back, so the fixtures tear down material before its accession.
        (jdbc.sql/update! *db* :material {:accession_id (:accession/id accession)}
                          {:id (:material/id material)}))
      (testing "taxon name"
        (media.i/link! *db* (:media/id media) (:taxon/id taxon) "taxon")
        (jdbc.sql/update! *db* :taxon {:name "Quercus fts-renamed"} {:id (:taxon/id taxon)})
        (is (= "Quercus fts-renamed" (linked media))))
      (testing "location name"
        (media.i/link! *db* (:media/id other-media) (:location/id location) "location")
        (jdbc.sql/update! *db* :location {:name "Oak walk"} {:id (:location/id location)})
        (is (= "FTS-B12 Oak walk" (linked other-media)))))))

(deftest test-unrelated-edits-leave-the-index-alone
  ;; A sentinel written straight into the index survives an edit to a column
  ;; the label doesn't read, and is replaced by an edit to one it does. That
  ;; is what shows each trigger's column list is right.
  (tf/testing "only the label's columns fire the refresh"
    (records)
    (fn [{:keys [media accession]}]
      (let [id (:media/id media)
            mark! #(jdbc/execute! *db* ["update media_fts set linked = 'sentinel' where rowid = ?" id])]
        (media.i/link! *db* id (:accession/id accession) "accession")
        (mark!)
        (jdbc.sql/update! *db* :accession {:private 1} {:id (:accession/id accession)})
        (is (= "sentinel" (linked media)) "accession.private isn't part of the label")
        (jdbc.sql/update! *db* :accession {:code "2099.7404"} {:id (:accession/id accession)})
        (is (= "2099.7404" (linked media)) "accession.code is")))))
