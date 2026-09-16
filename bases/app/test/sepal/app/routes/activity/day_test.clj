(ns sepal.app.routes.activity.day-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.routes.activity.index :as activity.index])
  (:import [java.time Instant LocalDate ZoneId]
           [java.time.format DateTimeFormatter]))

(deftest test-the-day-is-the-one-the-garden-was-in
  (testing "an accession created at 09:00 in New York belongs to that morning,
            not to the evening before. Grouping truncated the timestamp in UTC
            and the heading then read that UTC midnight back in local time, so
            every record made after 20:00 EDT — which is to say all of a
            working day — was filed under the previous date."
    (let [morning (Instant/parse "2026-09-16T13:00:00Z")]  ; 09:00 EDT
      (is (= "2026-09-16"
             (str (activity.index/activity-day morning "America/New_York")))))))

(deftest test-a-day-boundary-is-the-local-one
  (testing "the cut is local midnight, not UTC midnight"
    ;; 23:30 EDT on the 16th is already the 17th in UTC.
    (is (= "2026-09-16"
           (str (activity.index/activity-day (Instant/parse "2026-09-17T03:30:00Z")
                                             "America/New_York"))))
    ;; 00:30 EDT on the 17th is still the 17th.
    (is (= "2026-09-17"
           (str (activity.index/activity-day (Instant/parse "2026-09-17T04:30:00Z")
                                             "America/New_York"))))))

(deftest test-a-zone-ahead-of-utc-too
  (testing "07:00 in Tokyo on the 17th is still the 16th in UTC"
    (is (= "2026-09-17"
           (str (activity.index/activity-day (Instant/parse "2026-09-16T22:00:00Z")
                                             "Asia/Tokyo"))))))

(deftest test-an-unknown-timezone-falls-back-to-utc
  (is (= "2026-09-16"
         (str (activity.index/activity-day (Instant/parse "2026-09-16T13:00:00Z") nil)))))

(deftest test-the-heading-names-the-day
  (testing "and reads the same day the grouping chose. A date far enough back
            that it can never be Today or Yesterday when this runs."
    (let [zone "America/New_York"
          day (activity.index/activity-day (Instant/parse "2025-12-08T14:00:00Z") zone)]
      (is (= "Monday, December 8, 2025"
             (activity.index/format-day-header day zone))))))

(deftest test-the-heading-says-today-and-yesterday
  (testing "relative to the garden's timezone, not the server's"
    (let [zone "America/New_York"
          today (LocalDate/now (ZoneId/of zone))]
      (is (= "Today" (activity.index/format-day-header today zone)))
      (is (= "Yesterday" (activity.index/format-day-header (.minusDays today 1) zone))))))

(deftest test-a-relative-heading-carries-the-date-in-a-tooltip
  (let [zone "America/New_York"
        today (LocalDate/now (ZoneId/of zone))
        attrs (fn [day] (second (activity.index/day-header day zone)))]
    (testing "'Today' names a section but not a date, so the date is the tooltip"
      (is (= (.format (DateTimeFormatter/ofPattern "EEEE, MMMM d, yyyy") today)
             (:title (attrs today)))))
    (testing "a heading that already reads as a date gets no tooltip"
      (is (nil? (:title (attrs (LocalDate/of 2025 12 8))))))))

(deftest test-parse-day-answers-nil-rather-than-throwing
  (is (= (LocalDate/of 2025 12 8) (activity.index/parse-day "2025-12-08")))
  (is (nil? (activity.index/parse-day "not-a-date")))
  (is (nil? (activity.index/parse-day "")))
  (is (nil? (activity.index/parse-day nil))))
