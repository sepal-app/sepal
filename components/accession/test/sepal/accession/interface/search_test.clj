(ns sepal.accession.interface.search-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
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
