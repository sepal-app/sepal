(ns sepal.material.next-code-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [sepal.accession.interface :as acc.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i])
  (:import [java.time LocalDate]))

(use-fixtures :once default-system-fixture)

(def ^:private today (LocalDate/of 2026 9 14))
(def ^:private template "{seq}")

(deftest test-material-counts-within-its-accession
  (tf/testing "two accessions each number their own material from one"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::loc.i/factory :key/location] {:db *db*}
     [::acc.i/factory :key/acc-a] {:db *db* :taxon (ig/ref :key/taxon)}
     [::acc.i/factory :key/acc-b] {:db *db* :taxon (ig/ref :key/taxon)}
     [::mat.i/factory :key/a1] {:db *db*
                                :accession (ig/ref :key/acc-a)
                                :location (ig/ref :key/location)
                                :data {:code "1"}}
     [::mat.i/factory :key/a2] {:db *db*
                                :accession (ig/ref :key/acc-a)
                                :location (ig/ref :key/location)
                                :data {:code "2"}}}
    (fn [{:keys [acc-a acc-b]}]
      (is (= "3" (mat.i/next-code *db* template (:accession/id acc-a) today))
          "the accession that already holds 1 and 2")

      (is (= "1" (mat.i/next-code *db* template (:accession/id acc-b) today))
          "an accession with no material of its own starts again at one"))))

(deftest test-codes-of-another-shape-are-ignored
  (tf/testing "a material code that does not fit the template"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::loc.i/factory :key/location] {:db *db*}
     [::acc.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}
     [::mat.i/factory :key/m1] {:db *db*
                                :accession (ig/ref :key/acc)
                                :location (ig/ref :key/location)
                                :data {:code "1"}}
     [::mat.i/factory :key/odd] {:db *db*
                                 :accession (ig/ref :key/acc)
                                 :location (ig/ref :key/location)
                                 :data {:code "1a"}}}
    (fn [{:keys [acc]}]
      (is (= "2" (mat.i/next-code *db* template (:accession/id acc) today))))))

(deftest test-an-unusable-template-returns-nil
  (tf/testing "next-code degrades rather than throwing"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::acc.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [acc]}]
      (is (nil? (mat.i/next-code *db* "" (:accession/id acc) today)))
      (is (nil? (mat.i/next-code *db* nil (:accession/id acc) today)))
      (is (nil? (mat.i/next-code *db* "{nope}{seq}" (:accession/id acc) today))))))
