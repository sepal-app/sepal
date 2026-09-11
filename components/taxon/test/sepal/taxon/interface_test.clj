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
                ;; Set the WFO id through update!: CreateTaxon spells the key
                ;; :taxon/wfo-taxon-id, namespaced among otherwise-bare keys,
                ;; which UpdateTaxon does not.
                (let [t (taxon.i/create! db {:name name :rank :genus})]
                  (swap! made conj (:taxon/id t))
                  (taxon.i/update! db (:taxon/id t) {:wfo-taxon-id wfo-id})))]
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
