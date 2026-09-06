(ns sepal.tag.interface-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [malli.core :as m]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.tag.interface :as tag.i]
            [sepal.tag.interface.spec :as tag.spec]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(def ctx {:schema-version (db.i/latest-version)})

(def floor-ctx {:schema-version (db.i/minimum-supported-version)})

(deftest test-create-get-and-rename
  (let [tag (tag.i/create! *db* {:name "Fruit"})]
    (is (m/validate tag.spec/Tag tag))
    (is (= tag (tag.i/get-by-id ctx *db* (:tag/id tag))))
    (is (= (:tag/id tag) (:tag/id (tag.i/get-by-name ctx *db* "fruit")))
        "collate nocase: a lookup by a different case still finds the row")
    (let [renamed (tag.i/update! *db* (:tag/id tag) {:description "fruit trees"})]
      (is (= "fruit trees" (:tag/description renamed))))
    (tag.i/delete! *db* (:tag/id tag))
    (is (nil? (tag.i/get-by-id ctx *db* (:tag/id tag))))))

(deftest test-tag-untag-and-isolation
  (tf/testing "one accession and one taxon, tagged separately"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [accession taxon]}]
      (let [tag (tag.i/create! *db* {:name "sand tolerent"})]
        (tag.i/tag! *db* (:tag/id tag) (:accession/id accession) :accession)
        (tag.i/tag! *db* (:tag/id tag) (:taxon/id taxon) :taxon)
        (is (= ["sand tolerent"]
               (mapv :tag/name (tag.i/get-for-resource ctx *db* :accession (:accession/id accession)))))
        (is (= ["sand tolerent"]
               (mapv :tag/name (tag.i/get-for-resource ctx *db* :taxon (:taxon/id taxon)))))
        (is (empty? (tag.i/get-for-resource ctx *db* :material (:accession/id accession)))
            "same numeric id, wrong resource-type: must not leak across types")
        (tag.i/untag! *db* (:tag/id tag) (:accession/id accession) :accession)
        (is (empty? (tag.i/get-for-resource ctx *db* :accession (:accession/id accession))))
        (is (= ["sand tolerent"]
               (mapv :tag/name (tag.i/get-for-resource ctx *db* :taxon (:taxon/id taxon))))
            "untagging the accession must not touch the taxon's link")
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-tagging-twice-is-a-no-op
  (tf/testing "the same tag, the same resource, twice"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [accession]}]
      (let [tag (tag.i/create! *db* {:name "extras list 1"})]
        (tag.i/tag! *db* (:tag/id tag) (:accession/id accession) :accession)
        (tag.i/tag! *db* (:tag/id tag) (:accession/id accession) :accession)
        (is (= 1 (count (tag.i/get-for-resource ctx *db* :accession (:accession/id accession))))
            "tag_link_unique_idx makes the second insert a conflict, not a duplicate row")
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-delete-cascades-links
  (tf/testing "a tag with two links"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/a] {:db *db* :taxon (ig/ref :key/taxon)}
     [::accession.i/factory :key/b] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [a b]}]
      (let [tag (tag.i/create! *db* {:name "MIBO"})]
        (tag.i/tag! *db* (:tag/id tag) (:accession/id a) :accession)
        (tag.i/tag! *db* (:tag/id tag) (:accession/id b) :accession)
        (tag.i/delete! *db* (:tag/id tag))
        (is (empty? (tag.i/get-for-resource ctx *db* :accession (:accession/id a))))
        (is (empty? (tag.i/get-for-resource ctx *db* :accession (:accession/id b))))))))

(deftest test-list-all-counts-links-including-zero
  (tf/testing "one tagged tag and one untagged tag"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [accession]}]
      (let [tagged (tag.i/create! *db* {:name "O.H."})
            untagged (tag.i/create! *db* {:name "mh 2"})]
        (tag.i/tag! *db* (:tag/id tagged) (:accession/id accession) :accession)
        (let [rows (tag.i/list-all ctx *db*)]
          (is (= 1 (:tag/link-count (some #(when (= (:tag/id tagged) (:tag/id %)) %) rows))))
          (is (= 0 (:tag/link-count (some #(when (= (:tag/id untagged) (:tag/id %)) %) rows)))))
        (tag.i/delete! *db* (:tag/id tagged))
        (tag.i/delete! *db* (:tag/id untagged))))))

(deftest test-a-floor-database-reports-unavailable
  (is (tag.i/available? ctx))
  (is (not (tag.i/available? floor-ctx))))

(deftest test-factory
  (tf/testing "::tag.i/factory"
    {[::tag.i/factory :key/tag] {:db *db*}}
    (fn [{:keys [tag]}]
      (is (m/validate tag.spec/Tag tag)))))
