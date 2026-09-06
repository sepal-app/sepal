(ns sepal.taxon.interface.search-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-tag-filters-taxa
  (tf/testing "a taxon tagged Fruit"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [tag (tag.i/create! *db* {:name "Fruit"})]
        (tag.i/tag! *db* (:tag/id tag) (:taxon/id taxon) :taxon)
        (let [ast (search.i/parse "tag:Fruit")
              stmt (search.i/compile-query :taxon ast {:select [:t.*] :from [[:taxon :t]]})
              rows (db.i/execute! *db* stmt)]
          (is (some #(= (:taxon/id taxon) (:taxon/id %)) rows)))
        (tag.i/delete! *db* (:tag/id tag))))))
