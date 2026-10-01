(ns sepal.accession.interface.search-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
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

(deftest test-tag-filters-accessions
  (tf/testing "an accession tagged Fruit"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [accession]}]
      (let [tag (tag.i/create! *db* {:name "Fruit"})]
        (tag.i/tag! *db* (:tag/id tag) (:accession/id accession) :accession)
        (let [ast (search.i/parse "tag:Fruit")
              stmt (search.i/compile-query :accession ast {:select [:a.*] :from [[:accession :a]]})
              rows (db.i/execute! *db* stmt)]
          (is (some #(= (:accession/id accession) (:accession/id %)) rows)))
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-a-bare-word-searches-code-and-taxon
  (tf/testing "a bare word finds an accession by its code or its taxon's name"
    {[::taxon.i/factory :key/taxon] {:db *db* :name "Sepaltestia serrata"}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [accession]}]
      (let [found? (fn [q]
                     (let [stmt (search.i/compile-query :accession (search.i/parse q)
                                                        {:select [:a.*] :from [[:accession :a]]})]
                       (some #(= (:accession/id accession) (:accession/id %))
                             (db.i/execute! *db* stmt))))]
        (is (found? "sepaltestia") "by the taxon name")
        (is (found? (:accession/code accession)) "by the code")))))

(deftest test-location-filters-cover-sub-locations
  (tf/testing "a filter on the orchard finds accessions with material in its rows"
    {[::location.i/factory :key/orchard] {:db *db* :data {:code "ASRCH" :name "ASRCH orchard"}}
     [::location.i/factory :key/row] {:db *db* :parent (ig/ref :key/orchard)
                                      :data {:code "ASRCH-R1" :name "ASRCH row"}}
     [::location.i/factory :key/other] {:db *db* :data {:code "ASRCHX" :name "ASRCH other"}}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc-in-row] {:db *db* :taxon (ig/ref :key/taxon)}
     [::accession.i/factory :key/acc-elsewhere] {:db *db* :taxon (ig/ref :key/taxon)}
     [::material.i/factory :key/m1] {:db *db* :accession (ig/ref :key/acc-in-row)
                                     :location (ig/ref :key/row)}
     [::material.i/factory :key/m2] {:db *db* :accession (ig/ref :key/acc-elsewhere)
                                     :location (ig/ref :key/other)}}
    (fn [{:keys [orchard row] :as fx}]
      (let [found (fn [q]
                    (->> (search.i/compile-query :accession (search.i/parse q)
                                                 {:select [:a.id] :from [[:accession :a]]})
                         (db.i/execute! *db*)
                         (mapv :accession/id)))
            in-row (:accession/id (:acc-in-row fx))
            elsewhere (:accession/id (:acc-elsewhere fx))]
        (is (some #{in-row} (found (str "location.id:" (:location/id orchard)))) "parent id")
        (is (some #{in-row} (found (str "location.id:" (:location/id row)))) "leaf id")
        (is (= 1 (count (filter #{in-row} (found "location:ASRCH"))))
            "matching the orchard and the row returns it once")
        (let [negated (found (str "-location.id:" (:location/id orchard)))]
          (is (not-any? #{in-row} negated) "negated excludes the subtree")
          (is (some #{elsewhere} negated) "and keeps the rest"))))))
