(ns sepal.collection.interface-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [matcher-combinators.test :refer [match?]]
            [sepal.accession.interface :as acc.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.collection.interface :as coll.i]
            [sepal.collection.interface.spec :as coll.spec]
            [sepal.error.interface :as err.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "coll.i/create!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::coll.i/factory :key/coll] {:db db :accession (ig/ref :key/acc)}}
      (fn [{:keys [acc coll]}]
        (is (not (err.i/error? coll)) (err.i/data coll))
        (is (m/validate coll.spec/Collection coll))
        (is (match? {:collection/accession-id (:accession/id acc)}
                    coll))))))

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "coll.i/get-by-id"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::coll.i/factory :key/coll] {:db db :accession (ig/ref :key/acc)}}
      (fn [{:keys [coll]}]
        (let [result (coll.i/get-by-id db (:collection/id coll))]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate coll.spec/Collection result))
          (is (match? {:collection/id (:collection/id coll)}
                      result)))))))

(deftest test-get-by-accession-id
  (let [db *db*]
    (tf/testing "coll.i/get-by-accession-id"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::coll.i/factory :key/coll] {:db db :accession (ig/ref :key/acc)}}
      (fn [{:keys [acc coll]}]
        (let [result (coll.i/get-by-accession-id db (:accession/id acc))]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate coll.spec/Collection result))
          (is (match? {:collection/id (:collection/id coll)
                       :collection/accession-id (:accession/id acc)}
                      result)))))))

(deftest test-update
  (let [db *db*]
    (tf/testing "coll.i/update!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}
       [::coll.i/factory :key/coll] {:db db :accession (ig/ref :key/acc)}}
      (fn [{:keys [coll]}]
        (let [new-collector "Updated Collector"
              result (coll.i/update! db (:collection/id coll) {:collector new-collector})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate coll.spec/Collection result))
          (is (match? {:collection/collector new-collector}
                      result)))))))

;; NOTE: This test requires SpatiaLite extension to be loaded.
;; Run with: EXTENSIONS_LIBRARY_PATH=/path/to/lib
(deftest test-geo-point-roundtrip
  (let [db *db*]
    (tf/testing "geo-coordinates roundtrip"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc]}]
        (let [geo {:lat 45.5231
                   :lng -122.6765
                   :srid 4326}
              coll-data {:accession-id (:accession/id acc)
                         :geo-coordinates geo
                         :collector "Geo Test"}
              result (coll.i/create! db coll-data)]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (match? {:collection/geo-coordinates {:lat 45.5231
                                                    :lng -122.6765
                                                    :srid 4326}}
                      result)))))))
