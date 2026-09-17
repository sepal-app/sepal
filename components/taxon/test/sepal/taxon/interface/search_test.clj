(ns sepal.taxon.interface.search-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
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

(defn- search-ids
  "The taxon ids a free-text search returns."
  [q]
  (let [ast (search.i/parse q)
        stmt (search.i/compile-query :taxon ast {:select [:t.id] :from [[:taxon :t]]})]
    (->> (db.i/execute! *db* stmt) (map :taxon/id) set)))

(deftest test-a-vernacular-name-is-searchable
  (tf/testing "a plant is looked for by what people call it. taxon_fts indexed
               `name` alone, so a common name found nothing."
    {[::taxon.i/factory :key/named] {:db *db*
                                     :name "Acerus searchtestii"
                                     :vernacular-names [{:name "spottedsearchbloom"
                                                         :language "Nihongo"}]}
     [::taxon.i/factory :key/other] {:db *db* :name "Quercus searchtestii"}}
    (fn [{:keys [named other]}]
      (is (contains? (search-ids "spottedsearchbloom") (:taxon/id named))
          "found by its common name")
      (is (not (contains? (search-ids "spottedsearchbloom") (:taxon/id other)))
          "and only that one")
      (is (contains? (search-ids "Acerus searchtestii") (:taxon/id named))
          "the scientific name still finds it too"))))

(deftest test-only-the-name-is-indexed
  (tf/testing "not the language beside it, and not the JSON keys. Indexing the
               column as stored would have made `name`, `language` and every
               language value into search terms."
    {[::taxon.i/factory :key/taxon] {:db *db*
                                     :name "Acerus quiettestii"
                                     :vernacular-names [{:name "quietbloom"
                                                         :language "Nihongo"}]}}
    (fn [{:keys [taxon]}]
      (is (contains? (search-ids "quietbloom") (:taxon/id taxon)))
      (is (not (contains? (search-ids "Nihongo") (:taxon/id taxon)))
          "the language is not a search term")
      (is (not (contains? (search-ids "language") (:taxon/id taxon)))
          "nor is the JSON key"))))

(deftest test-a-vernacular-name-follows-an-edit
  (tf/testing "the index keeps its own copy of the text, so an edit has to
               replace it or the old common name keeps matching"
    {[::taxon.i/factory :key/taxon] {:db *db*
                                     :name "Acerus editestii"
                                     :vernacular-names [{:name "oldbloomname"
                                                         :language "English"}]}}
    (fn [{:keys [taxon]}]
      (is (contains? (search-ids "oldbloomname") (:taxon/id taxon)))
      (taxon.i/update! *db* (:taxon/id taxon)
                       {:vernacular-names [{:name "newbloomname" :language "English"}]})
      (is (empty? (search-ids "oldbloomname"))
          "the old common name stops matching")
      (is (contains? (search-ids "newbloomname") (:taxon/id taxon))
          "and the new one matches"))))

(deftest test-parentage-finds-a-cross-by-its-parent
  (tf/testing "what do I hold with Cattleya in its parentage"
    {}
    (fn [_]
      ;; Suffixed: the suite shares one database and another file also makes a
      ;; Cattleya, which a search by that name would then find twice.
      (let [suffix (subs (str (random-uuid)) 0 8)
            genus! (fn [nm] (taxon.i/create! *db* {:name (str nm suffix)
                                                   :rank :genus}))
            cattleya (genus! "Cattleya")
            laelia (genus! "Laelia")
            hybrid (genus! "Laeliocattleya")
            unrelated (genus! "Masdevallia")]
        (taxon.i/set-parentage! *db* (:taxon/id hybrid)
                                [{:parent-taxon-id (:taxon/id cattleya)}
                                 {:parent-taxon-id (:taxon/id laelia)}])
        (let [ast (search.i/parse (str "parentage:Cattleya" suffix))
              stmt (search.i/compile-query :taxon ast
                                           {:select [:t.id] :from [[:taxon :t]]})
              ids (set (map :taxon/id (db.i/execute! *db* stmt)))]
          (is (contains? ids (:taxon/id hybrid))
              "the cross is found by a parent's name")
          (is (not (contains? ids (:taxon/id unrelated)))
              "a taxon in no cross is not")
          (is (not (contains? ids (:taxon/id cattleya)))
              "the parent itself is not its own descendant")
          (testing "and a cross of several parents is one row, not one per parent"
            (is (= 1 (count (filter #(= (:taxon/id hybrid) %)
                                    (map :taxon/id (db.i/execute! *db* stmt))))))))))))
