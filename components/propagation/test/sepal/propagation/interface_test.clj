(ns sepal.propagation.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.spec :as prop.spec]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "propagation.i/create!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc] {:db db
                                         :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc]}]
        (let [result (propagation.i/create!
                       db {:type :cutting
                           :parent-accession-id (:accession/id acc)})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate prop.spec/Propagation result))
          (is (match? {:propagation/status :active
                       :propagation/parent-material-id nil}
                      result))
          (jdbc.sql/delete! db :propagation {:id (:propagation/id result)}))))))

(deftest test-accession-only-parent
  (let [db *db*]
    (tf/testing "a propagation with no parent material stores"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc] {:db db
                                         :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc]}]
        (let [result (propagation.i/create!
                       db {:type :seed
                           :parent-accession-id (:accession/id acc)})]
          (is (nil? (:propagation/parent-material-id result)))
          (jdbc.sql/delete! db :propagation {:id (:propagation/id result)}))))))

(deftest test-parent-material-must-match
  (let [db *db*]
    (tf/testing "parent material belonging to another accession is rejected"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc-a] {:db db
                                           :taxon (ig/ref :key/taxon)}
       [::accession.i/factory :key/acc-b] {:db db
                                           :taxon (ig/ref :key/taxon)}
       [::location.i/factory :key/loc] {:db db}
       [::material.i/factory :key/mat-b] {:db db
                                          :accession (ig/ref :key/acc-b)
                                          :location (ig/ref :key/loc)}}
      (fn [{:keys [acc-a mat-b]}]
        (is (thrown? Exception
                     (propagation.i/create!
                       db {:type :cutting
                           :parent-accession-id (:accession/id acc-a)
                           :parent-material-id (:material/id mat-b)})))))))

(deftest test-counts
  (let [db *db*]
    (tf/testing "the outcome pair"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc] {:db db
                                         :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc]}]
        (let [ok (propagation.i/create!
                   db {:type :seed
                       :parent-accession-id (:accession/id acc)
                       :quantity-started 40
                       :quantity-succeeded 12})]
          (is (match? {:propagation/quantity-started 40
                       :propagation/quantity-succeeded 12}
                      ok))
          (is (thrown? Exception
                       (propagation.i/create!
                         db {:type :seed
                             :parent-accession-id (:accession/id acc)
                             :quantity-started 40
                             :quantity-succeeded 45}))
              "succeeded above started must fail")
          (jdbc.sql/delete! db :propagation {:id (:propagation/id ok)}))))))

(deftest test-update-and-delete
  (let [db *db*]
    (tf/testing "propagation.i/update! and delete!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc] {:db db
                                         :taxon (ig/ref :key/taxon)}
       [::propagation.i/factory :key/prop] {:db db
                                            :accession (ig/ref :key/acc)}}
      (fn [{:keys [prop]}]
        (let [id (:propagation/id prop)
              updated (propagation.i/update! db id {:status :complete})]
          (is (match? {:propagation/status :complete} updated))
          (propagation.i/delete! db id)
          (is (nil? (propagation.i/get-by-id db id))))))))

(deftest test-list-by-parent
  (let [db *db*]
    (tf/testing "listing by parent accession"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc] {:db db
                                         :taxon (ig/ref :key/taxon)}
       [::propagation.i/factory :key/prop] {:db db
                                            :accession (ig/ref :key/acc)}}
      (fn [{:keys [prop]}]
        (let [rows (propagation.i/list-by-parent-accession-id
                     db (:propagation/parent-accession-id prop))]
          (is (= 1 (count rows)))
          (is (= (:propagation/id prop) (:propagation/id (first rows)))))))))

(deftest test-list-by-parent-material
  (let [db *db*]
    (tf/testing "listing by parent material"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc] {:db db
                                         :taxon (ig/ref :key/taxon)}
       [::location.i/factory :key/loc] {:db db}
       [::material.i/factory :key/mat] {:db db
                                        :accession (ig/ref :key/acc)
                                        :location (ig/ref :key/loc)}}
      (fn [{:keys [acc mat]}]
        (let [prop (propagation.i/create!
                     db {:type :cutting
                         :parent-accession-id (:accession/id acc)
                         :parent-material-id (:material/id mat)})]
          (try
            (let [rows (propagation.i/list-by-parent-material-id
                         db (:material/id mat))]
              (is (= 1 (count rows)))
              (is (= (:propagation/id prop) (:propagation/id (first rows)))))
            (finally
              (jdbc.sql/delete! db :propagation {:id (:propagation/id prop)}))))))))

(deftest test-products-link-back
  (let [db *db*]
    (tf/testing "the products point back at the propagation"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/parent] {:db db
                                            :taxon (ig/ref :key/taxon)}
       [::accession.i/factory :key/product] {:db db
                                             :taxon (ig/ref :key/taxon)}
       [::location.i/factory :key/loc] {:db db}
       [::material.i/factory :key/mat] {:db db
                                        :accession (ig/ref :key/parent)
                                        :location (ig/ref :key/loc)}}
      (fn [{:keys [parent product mat]}]
        (let [prop (propagation.i/create!
                     db {:type :cutting
                         :parent-accession-id (:accession/id parent)})]
          (try
            (material.i/update! db (:material/id mat)
                                {:propagation-id (:propagation/id prop)})
            (accession.i/update! db (:accession/id product)
                                 {:propagation-id (:propagation/id prop)})
            (is (= [(:material/id mat)]
                   (map :material/id
                        (material.i/list-by-propagation-id db (:propagation/id prop)))))
            (is (= [(:accession/id product)]
                   (map :accession/id
                        (accession.i/list-by-propagation-id db (:propagation/id prop)))))
            (finally
              ;; The links are cleared before the propagation goes, or the
              ;; foreign keys block the delete.
              (material.i/update! db (:material/id mat) {:propagation-id nil})
              (accession.i/update! db (:accession/id product) {:propagation-id nil})
              (jdbc.sql/delete! db :propagation {:id (:propagation/id prop)}))))))))

(deftest test-list-by-location
  (let [db *db*]
    (tf/testing "listing by location"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::accession.i/factory :key/acc] {:db db
                                         :taxon (ig/ref :key/taxon)}
       [::location.i/factory :key/loc] {:db db}}
      (fn [{:keys [acc loc]}]
        (let [prop (propagation.i/create!
                     db {:type :cutting
                         :parent-accession-id (:accession/id acc)
                         :location-id (:location/id loc)})]
          (try
            (let [rows (propagation.i/list-by-location-id db (:location/id loc))]
              (is (= 1 (count rows)))
              (is (= (:propagation/id prop) (:propagation/id (first rows)))))
            (finally
              (jdbc.sql/delete! db :propagation {:id (:propagation/id prop)}))))))))

(deftest test-vocabularies
  (let [db *db*]
    (is (= #{"seed" "cutting" "division" "graft" "layering"
             "tissue_culture" "other"}
           (set (map :propagation-type/name (propagation.i/list-types db)))))
    (is (= #{"active" "complete" "failed"}
           (set (map :propagation-status/name
                     (propagation.i/list-statuses db)))))))
