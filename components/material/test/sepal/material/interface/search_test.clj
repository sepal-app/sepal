(ns sepal.material.interface.search-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.search.interface :as search.i]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-tag-filters-materials
  (tf/testing "a material tagged Fruit"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [material]}]
      (let [tag (tag.i/create! *db* {:name "Fruit"})]
        (tag.i/tag! *db* (:tag/id tag) (:material/id material) :material)
        (let [ast (search.i/parse "tag:Fruit")
              stmt (search.i/compile-query :material ast {:select [:m.*] :from [[:material :m]]})
              rows (db.i/execute! *db* stmt)]
          (is (some #(= (:material/id material) (:material/id %)) rows)))
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-a-bare-word-finds-material-by-code-taxon-or-accession
  (tf/testing "the picker shows \"2026.0001.1 (Prunus salicina)\" on one line,
               so all three parts of that have to find it. Only the taxon did,
               which meant typing a code you could read on the screen found
               nothing."
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [taxon accession material]}]
      (material.i/update! *db* (:material/id material) {:code "SEARCHME"})
      (let [found (fn [q]
                    (->> (search.i/compile-query :material
                                                 (search.i/parse q)
                                                 {:select [:m.id] :from [[:material :m]]})
                         (db.i/execute! *db*)
                         (map :material/id)
                         (set)))
            id (:material/id material)]
        (is (contains? (found "SEARCHME") id)
            "its own code")
        (is (contains? (found (:accession/code accession)) id)
            "the code of the accession it came from")
        (is (contains? (found (first (str/split (:taxon/name taxon) #"\s+"))) id)
            "the plant it is")
        (is (not (contains? (found "nothingmatchesthis") id))
            "and a word that matches none of the three finds nothing")))))


(deftest test-location-filters-cover-sub-locations
  (tf/testing "a filter on the orchard finds material in its rows"
    {[::location.i/factory :key/orchard] {:db *db* :data {:code "MSRCH" :name "MSRCH orchard"}}
     [::location.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)
                                      :data {:code "MSRCH-R1" :name "MSRCH row"}}
     [::location.i/factory :key/other] {:db *db* :data {:code "MSRCHX" :name "MSRCH other"}}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::material.i/factory :key/in-row] {:db *db* :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/row)}
     [::material.i/factory :key/elsewhere] {:db *db* :accession (ig/ref :key/accession)
                                            :location (ig/ref :key/other)}}
    (fn [{:keys [orchard row] :as fx}]
      (let [found (fn [q]
                    (->> (search.i/compile-query :material (search.i/parse q)
                                                 {:select [:m.id] :from [[:material :m]]})
                         (db.i/execute! *db*)
                         (mapv :material/id)))
            in-row (:material/id (:in-row fx))
            elsewhere (:material/id (:elsewhere fx))]
        (is (some #{in-row} (found (str "location.id:" (:location/id orchard)))) "parent id")
        (is (some #{in-row} (found (str "location.id:" (:location/id row)))) "leaf id")
        (is (= 1 (count (filter #{in-row} (found "location.code:MSRCH"))))
            "matching the orchard and the row returns it once")
        (let [negated (found (str "-location.id:" (:location/id orchard)))]
          (is (not-any? #{in-row} negated) "negated excludes the subtree")
          (is (some #{elsewhere} negated) "and keeps the rest"))
        (is (some #{in-row} (found "location.name:\"MSRCH orchard\"")) "parent name")))))
