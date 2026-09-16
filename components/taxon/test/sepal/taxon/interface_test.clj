(ns sepal.taxon.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.spec :as taxon.spec]))

(use-fixtures :once default-system-fixture)

(deftest test-specs
  (testing "CreateTaxon - encode/store"
    (let [data {:taxon/id 123
                :taxon/rank :genus}]
      (is (match? (store.i/encode taxon.spec/CreateTaxon data)
                  {:id 1234
                   :rank "genus"}))))

  (testing "CreateTaxon - encode/db"
    (let [data {:taxon/id 123
                :taxon/rank :genus}]
      (is (match? (store.i/encode taxon.spec/CreateTaxon data)
                  {:id 1234
                   :rank "genus"})))))

(deftest test-create
  (tf/testing "create!"
    (let [db *db*
          taxon-name (mg/generate [:string {:min 1}])
          result (taxon.i/create! db {:name taxon-name
                                      :rank :genus})]
      (is (not (err.i/error? result)) (err.i/data result))
      (is (match? {:taxon/id pos-int?
                   :taxon/name taxon-name
                   :taxon/rank :genus
                   :taxon/author nil}
                  result))
      (jdbc.sql/delete! db :taxon {:id (:taxon/id result)}))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update! - org taxon"
      {[::taxon.i/factory :key/taxon]
       {:db db}}
      (fn [{:keys [taxon]}]
        (let [taxon-name (mg/generate [:string {:min 1}])
              taxon-rank :genus
              result (taxon.i/update! db
                                      (:taxon/id taxon)
                                      {:name taxon-name
                                       :rank taxon-rank})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (match? {:taxon/id pos-int?
                       :taxon/name taxon-name
                       :taxon/rank :genus
                       :taxon/author (:taxon/author taxon)}
                      result)))))))

(deftest test-wfo-taxon-id-is-spelled-the-same-either-way
  ;; It was not: CreateTaxon asked for :taxon/wfo-taxon-id, namespaced among
  ;; otherwise-bare keys, and UpdateTaxon for :wfo-taxon-id. Creating a taxon
  ;; with a WFO id therefore needed a differently shaped key than updating one,
  ;; and `store.i/coerce` strips an unrecognised key rather than refusing it, so
  ;; the wrong spelling silently wrote no id at all.
  (let [db *db*
        created (format "wfo-%010d-2024-01" (rand-int 1000000))
        updated (format "wfo-%010d-2024-02" (rand-int 1000000))]
    (tf/testing "create! and update! take the same key"
      {}
      (fn [_]
        (let [taxon (taxon.i/create! db {:name (mg/generate [:string {:min 1}])
                                         :rank :genus
                                         :wfo-taxon-id created})]
          (try
            (is (= created (:taxon/wfo-taxon-id taxon))
                "create! stored what it was given")
            (is (= updated (:taxon/wfo-taxon-id
                             (taxon.i/update! db (:taxon/id taxon)
                                              {:wfo-taxon-id updated})))
                "and update! takes the same spelling")
            (finally
              (jdbc.sql/delete! db :taxon {:id (:taxon/id taxon)}))))))))

(deftest test-distribution-roundtrips
  (tf/testing "distribution round-trips through create! and update!"
    (let [db *db*
          taxon-name (mg/generate [:string {:min 1}])
          created (taxon.i/create! db {:name taxon-name
                                       :rank :genus
                                       :distribution "Central America"})]
      (is (not (err.i/error? created)) (err.i/data created))
      (is (match? {:taxon/distribution "Central America"} created))
      (let [updated (taxon.i/update! db (:taxon/id created)
                                     {:distribution "Belize"})]
        (is (not (err.i/error? updated)) (err.i/data updated))
        (is (match? {:taxon/distribution "Belize"} updated)))
      (jdbc.sql/delete! db :taxon {:id (:taxon/id created)}))))

(deftest test-distribution-absent-by-default
  (tf/testing "a taxon with no distribution is unaffected"
    (let [db *db*
          taxon-name (mg/generate [:string {:min 1}])
          created (taxon.i/create! db {:name taxon-name :rank :genus})]
      (is (not (err.i/error? created)) (err.i/data created))
      (is (nil? (:taxon/distribution created)))
      (jdbc.sql/delete! db :taxon {:id (:taxon/id created)}))))

(deftest test-list-by-wfo-taxon-id
  (let [db *db*
        ;; The spec constrains the shape: wfo- then 10, 4 and 2 digits.
        wfo-id (format "wfo-%010d-2024-01" (rand-int 1000000))
        made (atom [])
        make! (fn [name]
                (let [t (taxon.i/create! db {:name name
                                             :rank :genus
                                             :wfo-taxon-id wfo-id})]
                  (swap! made conj (:taxon/id t))
                  t))]
    (try
      (testing "an unknown WFO id finds nothing"
        (is (= [] (taxon.i/list-by-wfo-taxon-id db "wfo-0000000000-1900-99"))))

      (testing "a taxon carrying one is found by it"
        (let [t (make! "Cattleya")
              got (taxon.i/list-by-wfo-taxon-id db wfo-id)]
          (is (= 1 (count got)))
          (is (= (:taxon/id t) (:taxon/id (first got))))
          (is (= "Cattleya" (:taxon/name (first got))))))

      ;; taxon.wfo_taxon_id is nullable with a plain index, not a unique one,
      ;; so this is reachable rather than hypothetical. The importer has to be
      ;; able to see it: resolving a reference to the wrong taxon is the error
      ;; nobody would notice.
      (testing "two taxa can share one, and both come back"
        (make! "Cattleya duplicate")
        (is (= 2 (count (taxon.i/list-by-wfo-taxon-id db wfo-id)))))

      (finally
        (doseq [id @made]
          (jdbc.sql/delete! db :taxon {:id id}))))))

(deftest test-delete
  (let [db *db*]
    (tf/testing "delete!"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (let [id (:taxon/id taxon)]
          (is (some? (taxon.i/get-by-id db id)))
          (taxon.i/delete! db id)
          (is (nil? (taxon.i/get-by-id db id))))))))

(deftest test-count-children
  (let [db *db*]
    (tf/testing "count-children"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (let [parent-id (:taxon/id taxon)]
          (is (zero? (taxon.i/count-children db parent-id)))
          (let [child (taxon.i/create! db {:name "Test child"
                                           :rank :species
                                           :parent-id parent-id})]
            (is (= 1 (taxon.i/count-children db parent-id)))
            (taxon.i/delete! db (:taxon/id child))
            (is (zero? (taxon.i/count-children db parent-id)))))))))

(deftest test-a-saved-name-carries-the-hybrid-marker-not-a-letter
  ;; In the component rather than the form, so the importer and the CLI get it
  ;; too: a name stored as "Acer x freemanii" would never match the WFO row it
  ;; names, since the reference writes the multiplication sign in every one of
  ;; its hybrid names.
  (testing "on create"
    (let [taxon (taxon.i/create! *db* {:name "Acer x freemanii" :rank "species"})]
      (try
        (is (= "Acer × freemanii" (:taxon/name taxon)))
        (finally (jdbc.sql/delete! *db* :taxon {:id (:taxon/id taxon)})))))

  (testing "on update"
    (let [taxon (taxon.i/create! *db* {:name "Acer rubrum" :rank "species"})]
      (try
        (is (= "Acer rubra × freemanii"
               (:taxon/name (taxon.i/update! *db* (:taxon/id taxon)
                                             {:name "Acer rubra x freemanii"}))))
        (finally (jdbc.sql/delete! *db* :taxon {:id (:taxon/id taxon)})))))

  (testing "an x that belongs to the word is left alone"
    (let [taxon (taxon.i/create! *db* {:name "Ilex opaca" :rank "species"})]
      (try
        (is (= "Ilex opaca" (:taxon/name taxon)))
        (finally (jdbc.sql/delete! *db* :taxon {:id (:taxon/id taxon)}))))))

