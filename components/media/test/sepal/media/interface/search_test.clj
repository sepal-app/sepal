(ns sepal.media.interface.search-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.search]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private base-stmt
  {:select [:md.id] :from [[:media :md]]})

(defn- found
  "The ids `q` finds, out of `ids`. Other tests' media share the database."
  [q ids]
  (->> (search.i/compile-query :media (search.i/parse q) base-stmt {:timezone "UTC"})
       (db.i/execute! *db*)
       (map :media/id)
       (filter (set ids))
       set))

(deftest test-media-search
  (tf/testing "each field, and plain text against the linked record"
    {[::user.i/factory :key/user] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db* :name "Fagus srchtest"}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             :data {:code "2099.7501"}}
     [::material.i/factory :key/material] {:db *db* :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)
                                           :data {:code "1"}}
     [::media.i/factory :key/on-accession] {:db *db* :user (ig/ref :key/user)
                                            :title "srchtest oak.jpg" :media-type "image/jpeg"}
     [::media.i/factory :key/on-material] {:db *db* :user (ig/ref :key/user)
                                           :title "srchtest maple.png" :media-type "image/png"}
     [::media.i/factory :key/on-taxon] {:db *db* :user (ig/ref :key/user)
                                        :title "srchtest beech.jpg" :media-type "image/jpeg"}
     [::media.i/factory :key/unlinked] {:db *db* :user (ig/ref :key/user)
                                        :title "srchtest.jpg" :description "the hedge"
                                        :media-type "image/jpeg"}}
    (fn [{:keys [accession material taxon on-accession on-material on-taxon unlinked]}]
      (let [[acc mat tax none :as ids] (map :media/id [on-accession on-material on-taxon unlinked])]
        (media.i/link! *db* acc (:accession/id accession) "accession")
        (media.i/link! *db* mat (:material/id material) "material")
        (media.i/link! *db* tax (:taxon/id taxon) "taxon")
        (testing "plain text"
          (is (= #{acc} (found "oak" ids)) "title")
          (is (= #{none} (found "hedge" ids)) "description")
          (is (= #{acc mat} (found "2099.7501" ids))
              "an accession code finds its media and its material's")
          (is (= #{mat} (found "2099.7501.1" ids)) "a material's full code finds its media")
          (is (= #{tax} (found "Fagus" ids)) "a taxon name"))
        (testing "type"
          (is (= #{mat} (found "type:png" ids))))
        (testing "linked"
          (is (= #{acc} (found "linked:accession" ids)))
          (is (= #{none} (found "linked:none" ids)))
          (is (= #{acc mat tax} (found "-linked:none" ids)))
          (is (= #{acc tax} (found "linked:taxon,accession" ids)))
          (is (= #{none} (found "linked:None" ids)) "values are case-insensitive"))
        (testing "uploaded"
          (jdbc.sql/update! *db* :media {:created_at "2001-02-03 10:00:00"} {:id acc})
          (is (= #{acc} (found "uploaded:<2001-02-04" ids)))
          (is (= #{acc} (found "uploaded:2001-02-03" ids))))))))
