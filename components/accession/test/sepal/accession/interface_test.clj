(ns sepal.accession.interface-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [sepal.accession.interface :as acc.i]
            [sepal.accession.interface.spec :as acc.spec]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.error.interface :as err.i]
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
