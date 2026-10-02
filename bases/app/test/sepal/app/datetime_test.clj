(ns sepal.app.datetime-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.datetime :as datetime]
            [sepal.i18n.interface :as i18n])
  (:import [java.time Instant]
           [java.util Locale]))

(def test-instant
  "A fixed instant for testing: 2025-01-18T14:30:00Z"
  (Instant/parse "2025-01-18T14:30:00Z"))

(deftest test-today-is-the-gardens-day
  ;; Kiritimati is UTC+14 and Midway UTC-11, 25 hours apart, so their local
  ;; dates never coincide. A `today` that ignored its timezone would return the
  ;; server's date for both and these would be equal.
  (testing "the date is read in the garden's zone, not the server's"
    (is (not= (datetime/today "Pacific/Kiritimati")
              (datetime/today "Pacific/Midway"))))

  (testing "an unset timezone throws rather than reading the date in UTC"
    (is (thrown? IllegalArgumentException (datetime/today nil)))))

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

  (testing "throws for a nil timezone rather than formatting in UTC"
    ;; A caller that forgot :timezone showed a Belize garden times six hours
    ;; ahead, with nothing to say they were wrong.
    (is (thrown? IllegalArgumentException
                 (datetime/format-datetime test-instant nil))))

  (testing "English does not depend on the JVM's default locale"
    ;; A formatter reads the default locale when it is built, at load, so
    ;; changing the default here would prove nothing. Check what it holds.
    (is (= Locale/ENGLISH (.getLocale (#'datetime/formatter :datetime))))))

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
      (is (= "just now" (datetime/format-relative now "UTC")))))

  (testing "minutes ago"
    (let [five-min-ago (.minusSeconds (Instant/now) (* 5 60))]
      (is (= "5 minutes ago" (datetime/format-relative five-min-ago "UTC"))))
    (let [one-min-ago (.minusSeconds (Instant/now) 90)]
      (is (= "1 minute ago" (datetime/format-relative one-min-ago "UTC")))))

  (testing "hours ago"
    (let [two-hours-ago (.minusSeconds (Instant/now) (* 2 60 60))]
      (is (= "2 hours ago" (datetime/format-relative two-hours-ago "UTC"))))
    (let [one-hour-ago (.minusSeconds (Instant/now) (* 1 60 60))]
      (is (= "1 hour ago" (datetime/format-relative one-hour-ago "UTC")))))

  (testing "yesterday"
    (let [now (Instant/parse "2025-01-18T14:30:00Z")
          yesterday (.minusSeconds now (* 30 60 60))]
      (is (= "yesterday" (datetime/format-relative yesterday "UTC" now)))))

  (testing "days ago"
    (let [three-days-ago (.minusSeconds (Instant/now) (* 3 24 60 60))]
      (is (= "3 days ago" (datetime/format-relative three-days-ago "UTC")))))

  (testing "weeks ago"
    (let [two-weeks-ago (.minusSeconds (Instant/now) (* 14 24 60 60))]
      (is (= "2 weeks ago" (datetime/format-relative two-weeks-ago "UTC")))))

  (testing "returns nil for nil instant"
    (is (nil? (datetime/format-relative nil "UTC")))))

(deftest format-relative-follows-the-gardens-calendar
  ;; Wednesday 7:00 AM in Belize, UTC-6 all year.
  (let [now (Instant/parse "2026-09-30T13:00:00Z")]
    (testing "a day before the garden's today is yesterday, though 25 hours ago"
      ;; Tuesday 6:00 AM in Belize.
      (is (= "yesterday" (datetime/format-relative
                           (Instant/parse "2026-09-29T12:00:00Z") "America/Belize" now))))
    (testing "two days back is not yesterday, though only 32 hours ago"
      ;; Monday 11:00 PM in Belize, under a Monday heading in the feed.
      (is (= "2 days ago" (datetime/format-relative
                            (Instant/parse "2026-09-29T05:00:00Z") "America/Belize" now))))
    (testing "under a day stays in hours, whichever day it was"
      (is (= "8 hours ago" (datetime/format-relative
                             (Instant/parse "2026-09-30T05:00:00Z") "America/Belize" now))))))

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

(deftest format-date-test
  (testing "an ISO date reads as a short month, day and year"
    (is (= "Mar 14, 2026" (datetime/format-date "2026-03-14"))))
  (testing "nil stays nil"
    (is (nil? (datetime/format-date nil))))
  (testing "a stored value that is not a date is shown as it is"
    ;; Rather than throwing, which took down every page listing that record.
    (is (= "11/02/2011" (datetime/format-date "11/02/2011")))
    (is (= "2026-02-30" (datetime/format-date "2026-02-30")))))

(deftest test-spanish-uses-spanish-formats
  (i18n/load-catalogs! {"es" (i18n/parse-catalog "es" "msgid \"\"
msgstr \"\"
\"Plural-Forms: nplurals=2; plural=(n != 1);\\n\"

msgid \"Today\"
msgstr \"Hoy\"

msgid \"%1 hour ago\"
msgid_plural \"%1 hours ago\"
msgstr[0] \"hace %1 hora\"
msgstr[1] \"hace %1 horas\"
")})
  (try
    (i18n/with-locale "es"
      (let [day (java.time.LocalDate/of 2026 1 18)]
        (is (= "domingo, 18 de enero de 2026" (datetime/format-day day)))
        (is (= "Hoy" (datetime/day-label day day)))
        (is (re-find #"ene" (datetime/format-date "2026-01-18")))
        (is (re-find #"enero" (datetime/format-datetime-full test-instant "UTC")))
        (is (not (re-find #"\bat\b" (datetime/format-datetime-full test-instant "UTC")))
            "no English word order carried into Spanish")
        (is (= "hace 2 horas" (datetime/format-relative
                                (.minus (java.time.Instant/now) (java.time.Duration/ofHours 2))
                                "UTC")))))
    (finally
      (i18n/load-catalogs! {}))))

(deftest test-timezone-options
  (let [values (mapv :value (datetime/timezone-options))]
    (testing "UTC, the default, is offered, so a UTC garden sees it selected"
      (is (= "UTC" (first values))))
    (testing "zones whose names carry a hyphen are offered"
      (is (some #{"America/Port-au-Prince"} values) "Haiti's only zone"))
    (testing "an offset of zero reads +00:00, not Z"
      (is (not-any? #(re-find #"UTCZ" (:label %)) (datetime/timezone-options))))))

(deftest test-valid-timezone
  (is (datetime/valid-timezone? "America/Belize"))
  (is (datetime/valid-timezone? "UTC"))
  (is (not (datetime/valid-timezone? "America/Belise")) "a misspelt zone")
  (is (not (datetime/valid-timezone? "")))
  (is (not (datetime/valid-timezone? nil))))
