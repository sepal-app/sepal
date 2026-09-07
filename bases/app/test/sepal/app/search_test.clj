(ns sepal.app.search-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.search]
            [sepal.app.search :as app.search]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.material.interface.search]
            [sepal.search.interface :as search.i]
            [sepal.tag.interface :as tag.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.search]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ctx {:schema-version (db.i/latest-version)})

(def floor-ctx {:schema-version (db.i/minimum-supported-version)})

(defn- tag-offered? [ctx resource-type]
  (boolean (some #(= "tag" (:key %)) (app.search/field-options ctx resource-type))))

(deftest test-the-filter-dropdown-offers-tag-only-where-it-works
  ;; search.i/field-options excludes :id and :boolean fields only, so the
  ;; :type :text "Tag" field was offered on every database, floor included --
  ;; a control in the Filter dropdown that 500s when you pick it.
  (doseq [resource-type [:accession :material :taxon]]
    (testing (name resource-type)
      (is (tag-offered? ctx resource-type))
      (is (not (tag-offered? floor-ctx resource-type))))))

(deftest test-a-tag-filter-below-the-gate-joins-nothing-and-matches-nothing
  (let [base-stmt {:select [:*] :from [[:accession :a]]}
        ast (search.i/parse "tag:Fruit")
        joined-tables #(->> % :join (partition 2) (map ffirst) set)]
    (testing "at latest the filter compiles to the tag_link join"
      (let [stmt (app.search/compile-query ctx :accession ast base-stmt)]
        (is (contains? (joined-tables stmt) :tag_link))))
    (testing "below the gate it neither joins nor widens"
      (let [stmt (app.search/compile-query floor-ctx :accession ast base-stmt)]
        (is (not (contains? (joined-tables stmt) :tag_link))
            "no join against a table this database does not have")
        (is (= [:= 1 0] (:where stmt))
            "and a never-matching clause, because dropping the filter would
             widen the search to every row")))
    (testing "a negated tag filter is satisfied by every row instead"
      (let [stmt (app.search/compile-query floor-ctx :accession
                                           (search.i/parse "-tag:Fruit")
                                           base-stmt)]
        (is (nil? (:where stmt)))))))

(deftest test-a-hand-typed-tag-query-below-the-gate-does-not-500
  ;; The dropdown no longer offers the field, but a bookmark or a typed query
  ;; still reaches the handler. The accession must really be tagged, so the
  ;; empty result below is the gate rather than an empty table.
  (tf/testing "GET /accession/?q=tag:… on a floor database"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [tag (tag.i/create! *db* {:name "Fernaldia"})
            sess (app.test/login (:user/email user) "testpassword123")
            code (:accession/code accession)
            url "/accession/?q=tag:Fernaldia"]
        (tag.i/tag! *db* (:tag/id tag) (:accession/id accession) :accession)
        (let [{:keys [response]} (peri/request sess url)]
          (is (= 200 (:status response)))
          (is (re-find (re-pattern code) (:body response))
              "the filter finds the tagged accession when the tables are there"))
        (with-redefs [db.i/at-least-version? (constantly false)]
          (let [{:keys [response]} (peri/request sess url)]
            (is (= 200 (:status response))
                "no such table: tag_link would be a 500")
            (is (not (re-find (re-pattern code) (:body response)))
                "and the filter matches nothing rather than widening to every row"))
          (let [{:keys [response]} (peri/request sess "/accession/export/?q=tag:Fernaldia")]
            (is (= 200 (:status response))
                "the CSV export compiles the same query and must gate the same way")))
        (tag.i/untag! *db* (:tag/id tag) (:accession/id accession) :accession)
        (tag.i/delete! *db* (:tag/id tag))))))
