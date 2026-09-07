(ns sepal.accession.interface-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as acc.i]
            [sepal.accession.interface.spec :as acc.spec]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "accession.i/create!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::contact.i/factory :key/contact] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :contact (ig/ref :key/contact)}}
      (fn [{:keys [acc taxon contact]}]
        (is (not (err.i/error? acc)) (err.i/data acc))
        (is (m/validate acc.spec/Accession acc))
        (is (match? {:accession/taxon-id (:taxon/id taxon)
                     :accession/supplier-contact-id (:contact/id contact)}
                    acc))))))

(deftest test-update
  (let [db *db*]
    (tf/testing "update!"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::contact.i/factory :key/contact] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :contact (ig/ref :key/contact)}}
      (fn [{:keys [acc taxon]}]
        (let [acc-code (mg/generate acc.spec/code)
              result (acc.i/update! db
                                    (:accession/id acc)
                                    {:code acc-code})]
          (is (not (err.i/error? result)) (err.i/data result))
          (is (m/validate acc.spec/Accession result))
          (is (match? {:accession/taxon-id (:taxon/id taxon)}
                      result)))))))

(deftest test-count-by-taxon-id
  (let [db *db*]
    (tf/testing "count-by-taxon-id returns 0 for taxon with no accessions"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (is (= 0 (acc.i/count-by-taxon-id db (:taxon/id taxon))))))

    (tf/testing "count-by-taxon-id returns correct count"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc1] {:db db :taxon (ig/ref :key/taxon)}
       [::acc.i/factory :key/acc2] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [taxon]}]
        (is (= 2 (acc.i/count-by-taxon-id db (:taxon/id taxon))))))))

(deftest test-create-with-intended-location
  (let [db *db*]
    (tf/testing "an accession can name the location its material is meant for"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}}
      (fn [{:keys [acc loc]}]
        (is (not (err.i/error? acc)) (err.i/data acc))
        (is (m/validate acc.spec/Accession acc))
        (is (= (:location/id loc) (:accession/intended-location-id acc)))))))

(deftest test-a-generated-accession-has-no-intended-location
  (let [db *db*]
    (tf/testing "the factory does not invent a location id"
      ;; `mg/generate` fills every key of the closed CreateAccession spec,
      ;; including optional ones, so an unset intended-location-id would arrive
      ;; as a random integer and fail the foreign key.
      {[::taxon.i/factory :key/taxon] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc]}]
        (is (nil? (:accession/intended-location-id acc)))))))

(deftest test-an-unknown-intended-location-is-refused
  (let [db *db*]
    (tf/testing "the foreign key holds"
      {[::taxon.i/factory :key/taxon] {:db db}}
      (fn [{:keys [taxon]}]
        (is (thrown? org.sqlite.SQLiteException
                     (acc.i/create! db {:code "ACC-INT-1"
                                        :taxon-id (:taxon/id taxon)
                                        :intended-location-id 999999999}))
            "a location id no location has must be refused")))))

(deftest test-a-location-an-accession-intends-cannot-be-deleted
  (let [db *db*]
    (tf/testing "the reference restricts, and clearing it releases the location"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}}
      (fn [{:keys [acc loc]}]
        (is (thrown? org.sqlite.SQLiteException
                     (jdbc.sql/delete! db :location {:id (:location/id loc)}))
            "a location an accession intends must not be deletable")
        (acc.i/update! db (:accession/id acc) {:intended-location-id nil})
        (is (nil? (:accession/intended-location-id
                    (acc.i/get-by-id db (:accession/id acc)))))
        (is (some? (jdbc.sql/delete! db :location {:id (:location/id loc)}))
            "once nothing intends it the location can go")))))

(deftest test-awaiting-planting-by-location-id
  (let [db *db*]
    (tf/testing "an accession intended for a location with nothing planted"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}}
      (fn [{:keys [acc loc taxon]}]
        (is (match? [{:accession/id (:accession/id acc)
                      :accession/code (:accession/code acc)
                      :taxon/name (:taxon/name taxon)}]
                    (acc.i/awaiting-planting-by-location-id
                      db (:location/id loc))))))

    (tf/testing "material in the intended location takes it off the list"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/loc)}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/loc)}}
      (fn [{:keys [loc mat]}]
        (is (some? mat))
        (is (= [] (acc.i/awaiting-planting-by-location-id
                    db (:location/id loc))))))

    (tf/testing "material somewhere else leaves it on the list"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/intended] {:db db}
       [::loc.i/factory :key/elsewhere] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/intended)}
       [::mat.i/factory :key/mat] {:db db
                                   :accession (ig/ref :key/acc)
                                   :location (ig/ref :key/elsewhere)}}
      (fn [{:keys [acc intended mat]}]
        (is (some? mat))
        (is (match? [{:accession/id (:accession/id acc)}]
                    (acc.i/awaiting-planting-by-location-id
                      db (:location/id intended))))))

    (tf/testing "partly planted counts as planted"
      ;; Material in two locations, one of them the intended one. The bed has
      ;; what it was promised, so the accession is not waiting on it.
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/intended] {:db db}
       [::loc.i/factory :key/elsewhere] {:db db}
       [::acc.i/factory :key/acc] {:db db
                                   :taxon (ig/ref :key/taxon)
                                   :intended-location (ig/ref :key/intended)}
       [::mat.i/factory :key/mat1] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/intended)}
       [::mat.i/factory :key/mat2] {:db db
                                    :accession (ig/ref :key/acc)
                                    :location (ig/ref :key/elsewhere)}}
      (fn [{:keys [intended mat1 mat2]}]
        (is (some? mat1))
        (is (some? mat2))
        (is (= [] (acc.i/awaiting-planting-by-location-id
                    db (:location/id intended))))))

    (tf/testing "an accession with no intended location is never listed"
      {[::taxon.i/factory :key/taxon] {:db db}
       [::loc.i/factory :key/loc] {:db db}
       [::acc.i/factory :key/acc] {:db db :taxon (ig/ref :key/taxon)}}
      (fn [{:keys [acc loc]}]
        (is (some? acc))
        (is (= [] (acc.i/awaiting-planting-by-location-id
                    db (:location/id loc))))))))
