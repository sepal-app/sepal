(ns sepal.material.interface.search-test
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
