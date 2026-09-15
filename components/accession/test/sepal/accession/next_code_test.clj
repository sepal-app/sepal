(ns sepal.accession.next-code-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [sepal.accession.interface :as acc.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.taxon.interface :as taxon.i])
  (:import [java.time LocalDate]))

(use-fixtures :once default-system-fixture)

(def ^:private today (LocalDate/of 2026 9 14))

;; A literal prefix no generated code will collide with. The suite shares one
;; database, so a template of {year}.{seq:0000} would scan whatever other tests
;; happened to leave behind.
(def ^:private template "ZZ{year}-{seq:0000}")

(deftest test-no-matching-codes-starts-at-one
  (tf/testing "next-code with nothing to scan"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [_]
      (is (= "ZZ2026-0001" (acc.i/next-code *db* template today))))))

(deftest test-max-plus-one-and-gaps-are-not-reused
  (tf/testing "next-code over stored codes"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::acc.i/factory :key/a1] {:db *db*
                                :taxon (ig/ref :key/taxon)
                                :data {:code "ZZ2026-0001"}}
     [::acc.i/factory :key/a3] {:db *db*
                                :taxon (ig/ref :key/taxon)
                                :data {:code "ZZ2026-0003"}}}
    (fn [_]
      (is (= "ZZ2026-0004" (acc.i/next-code *db* template today))
          "the highest plus one; 0002 is never handed out again"))))

(deftest test-last-years-codes-are-ignored
  (tf/testing "next-code across a year boundary"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::acc.i/factory :key/old] {:db *db*
                                 :taxon (ig/ref :key/taxon)
                                 :data {:code "ZZ2025-0400"}}}
    (fn [_]
      (is (= "ZZ2026-0001" (acc.i/next-code *db* template today))
          "nothing to reset at new year: the prefix changes and the scan misses"))))

(deftest test-codes-of-another-shape-are-ignored
  (tf/testing "next-code with a same-prefix code that does not fit"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::acc.i/factory :key/ok] {:db *db*
                                :taxon (ig/ref :key/taxon)
                                :data {:code "ZZ2026-0001"}}
     [::acc.i/factory :key/odd] {:db *db*
                                 :taxon (ig/ref :key/taxon)
                                 :data {:code "ZZ2026-xx"}}}
    (fn [_]
      (is (= "ZZ2026-0002" (acc.i/next-code *db* template today))))))

(deftest test-an-unusable-template-returns-nil
  (tf/testing "next-code degrades rather than throwing"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [_]
      (is (nil? (acc.i/next-code *db* "" today)))
      (is (nil? (acc.i/next-code *db* nil today)))
      (is (nil? (acc.i/next-code *db* "{nope}{seq}" today))))))
