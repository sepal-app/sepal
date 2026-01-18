(ns sepal.app.datetime-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.datetime :as datetime])
  (:import [java.time Instant]))

(def test-instant
  "A fixed instant for testing: 2025-01-18T14:30:00Z"
  (Instant/parse "2025-01-18T14:30:00Z"))

(deftest format-datetime-test
  (testing "formats instant in UTC timezone"
    (is (some? (datetime/format-datetime test-instant "UTC")))
    ;; Should contain the time
    (is (re-find #"2:30" (datetime/format-datetime test-instant "UTC"))))

  (testing "formats instant in different timezone"
    (let [result (datetime/format-datetime test-instant "America/New_York")]
      (is (some? result))
      ;; EST is UTC-5, so 14:30 UTC = 9:30 AM EST
      (is (re-find #"9:30" result))))

  (testing "returns nil for nil instant"
    (is (nil? (datetime/format-datetime nil "UTC"))))

  (testing "defaults to UTC for nil timezone"
    (is (some? (datetime/format-datetime test-instant nil)))))

(deftest format-datetime-full-test
  (testing "formats with full date, time, and timezone"
    (let [result (datetime/format-datetime-full test-instant "UTC")]
      (is (some? result))
      ;; Should contain full month name
      (is (re-find #"January" result))
      ;; Should contain year
      (is (re-find #"2025" result))
      ;; Should contain time
      (is (re-find #"2:30 PM" result))))

  (testing "includes timezone abbreviation"
    (let [result (datetime/format-datetime-full test-instant "America/New_York")]
      ;; Should contain EST or EDT depending on DST
      (is (re-find #"EST|EDT" result))))

  (testing "returns nil for nil instant"
    (is (nil? (datetime/format-datetime-full nil "UTC")))))

(deftest format-relative-test
  (testing "just now for very recent"
    (let [now (Instant/now)]
      (is (= "just now" (datetime/format-relative now)))))

  (testing "minutes ago"
    (let [five-min-ago (.minusSeconds (Instant/now) (* 5 60))]
      (is (= "5 minutes ago" (datetime/format-relative five-min-ago))))
    (let [one-min-ago (.minusSeconds (Instant/now) 90)]
      (is (= "1 minute ago" (datetime/format-relative one-min-ago)))))

  (testing "hours ago"
    (let [two-hours-ago (.minusSeconds (Instant/now) (* 2 60 60))]
      (is (= "2 hours ago" (datetime/format-relative two-hours-ago))))
    (let [one-hour-ago (.minusSeconds (Instant/now) (* 1 60 60))]
      (is (= "1 hour ago" (datetime/format-relative one-hour-ago)))))

  (testing "yesterday"
    (let [yesterday (.minusSeconds (Instant/now) (* 30 60 60))]
      (is (= "yesterday" (datetime/format-relative yesterday)))))

  (testing "days ago"
    (let [three-days-ago (.minusSeconds (Instant/now) (* 3 24 60 60))]
      (is (= "3 days ago" (datetime/format-relative three-days-ago)))))

  (testing "weeks ago"
    (let [two-weeks-ago (.minusSeconds (Instant/now) (* 14 24 60 60))]
      (is (= "2 weeks ago" (datetime/format-relative two-weeks-ago)))))

  (testing "returns nil for nil instant"
    (is (nil? (datetime/format-relative nil)))))

(deftest relative-time-hiccup-test
  (testing "renders time element with relative content"
    (let [recent (.minusSeconds (Instant/now) (* 5 60))
          result (datetime/relative-time recent "UTC")]
      (is (vector? result))
      (is (= :time (first result)))
      ;; Should have datetime attribute
      (is (contains? (second result) :datetime))
      ;; Should have title for tooltip
      (is (contains? (second result) :title))
      ;; Content should be relative time
      (is (= "5 minutes ago" (last result)))))

  (testing "includes class when provided"
    (let [result (datetime/relative-time test-instant "UTC" :class "text-sm")]
      (is (= "text-sm" (:class (second result))))))

  (testing "returns nil for nil instant"
    (is (nil? (datetime/relative-time nil "UTC")))))

(deftest datetime-hiccup-test
  (testing "renders time element with formatted datetime"
    (let [result (datetime/datetime test-instant "UTC")]
      (is (vector? result))
      (is (= :time (first result)))
      ;; Should have datetime attribute with ISO format
      (is (= (str test-instant) (:datetime (second result))))
      ;; Should have title for tooltip
      (is (some? (:title (second result))))
      ;; Content should be formatted datetime
      (is (re-find #"2:30" (last result)))))

  (testing "includes class when provided"
    (let [result (datetime/datetime test-instant "UTC" :class "font-bold")]
      (is (= "font-bold" (:class (second result))))))

  (testing "returns nil for nil instant"
    (is (nil? (datetime/datetime nil "UTC")))))

(deftest format-for-email-test
  (testing "formats for email display"
    (let [result (datetime/format-for-email test-instant "UTC")]
      (is (some? result))
      ;; Should be same format as format-datetime-full
      (is (= (datetime/format-datetime-full test-instant "UTC") result))))

  (testing "returns nil for nil instant"
    (is (nil? (datetime/format-for-email nil "UTC")))))
