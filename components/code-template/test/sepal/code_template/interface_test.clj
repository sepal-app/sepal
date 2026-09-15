(ns sepal.code-template.interface-test
  (:require [clojure.test :refer [are deftest is testing]]
            [sepal.code-template.interface :as ct.i]
            [sepal.error.interface :as error.i])
  (:import [java.time LocalDate]))

(def ^:private today (LocalDate/of 2026 9 14))

(defn- render [template n]
  (ct.i/render (ct.i/parse template) today n))

(deftest test-every-token-renders
  (is (= "2026-1" (render "{year}-{seq}" 1)))
  (is (= "26-1" (render "{year2}-{seq}" 1)))
  (is (= "09-1" (render "{month}-{seq}" 1)))
  (is (= "14-1" (render "{day}-{seq}" 1)))
  (is (= "4" (render "{seq}" 4)))

  (testing "the shapes the Belize import actually uses"
    (is (= "2026.0149" (render "{year}.{seq:0000}" 149)))
    (is (= "N0046" (render "N{seq:0000}" 46)))
    (is (= "26-149" (render "{year2}-{seq:000}" 149)))))

(deftest test-width-pads-but-never-truncates
  (is (= "0001" (render "{seq:0000}" 1)))
  (is (= "001" (render "{seq:000}" 1)))
  (testing "a number past its width gets a wider code, not a collision"
    (is (= "10000" (render "{seq:0000}" 10000)))))

(deftest test-parse-rejects
  (are [template] (error.i/error? (ct.i/parse template))
    "PLAIN"                ; zero seq tokens
    "{year}"               ; zero seq tokens, but tokens present
    "{seq}-{seq}"          ; two seq tokens
    "{seq}-{seq:0000}"     ; two seq tokens, differently spelled
    "{nope}-{seq}"         ; unknown token name
    "{seq"                 ; unclosed brace
    "N{seq:0000"))         ; unclosed brace with a width

(deftest test-parse-accepts
  (are [template] (not (error.i/error? (ct.i/parse template)))
    "{seq}"
    "{year}.{seq:0000}"
    "N{seq:0000}"
    "{year2}-{seq:000}"
    "{year}{month}{day}-{seq}"))

(deftest test-matches?
  (let [parts (ct.i/parse "{year}.{seq:0000}")]
    (testing "a date token is a digit run of the right width, not today's value"
      (is (ct.i/matches? parts "2025.0400"))
      (is (ct.i/matches? parts "2026.0149")))

    (testing "another convention does not match"
      (is (not (ct.i/matches? parts "N0046"))))

    (testing "too few digits for the width, and trailing junk"
      (is (not (ct.i/matches? parts "2026.12")))
      (is (not (ct.i/matches? parts "2026.0149A"))))))

(deftest test-next-code
  (let [t "{year}.{seq:0000}"]
    (testing "an empty collection starts at one"
      (is (= "2026.0001" (ct.i/next-code t [] today))))

    (testing "max plus one, and a gap below the maximum is not reused"
      (is (= "2026.0004" (ct.i/next-code t ["2026.0001" "2026.0003"] today))))

    (testing "last year's codes are ignored, so nothing resets at new year"
      (is (= "2026.0001" (ct.i/next-code t ["2025.0400" "2025.0401"] today))))

    (testing "codes sharing the prefix but not the shape are ignored"
      (is (= "2026.0002" (ct.i/next-code t ["2026.0001" "2026.x" "2026.12"] today))))

    (testing "a number past the width still counts"
      (is (= "2026.10001" (ct.i/next-code t ["2026.10000"] today))))

    (testing "material counts within its accession, from one"
      (is (= "3" (ct.i/next-code "{seq}" ["1" "2"] today))))

    (testing "a blank or unparseable template degrades to nil, not a 500"
      (is (nil? (ct.i/next-code "" [] today)))
      (is (nil? (ct.i/next-code nil [] today)))
      (is (nil? (ct.i/next-code "   " [] today)))
      (is (nil? (ct.i/next-code "{nope}{seq}" [] today))))))

(deftest test-scan-prefix
  (testing "the leading literal with date tokens expanded, for a SQL like"
    (is (= "2026." (ct.i/scan-prefix "{year}.{seq:0000}" today)))
    (is (= "N" (ct.i/scan-prefix "N{seq:0000}" today)))
    (is (= "26-" (ct.i/scan-prefix "{year2}-{seq:000}" today))))

  (testing "a template starting with seq has no prefix to narrow by"
    (is (= "" (ct.i/scan-prefix "{seq}" today))))

  (testing "an unusable template has no prefix"
    (is (nil? (ct.i/scan-prefix nil today)))
    (is (nil? (ct.i/scan-prefix "{nope}{seq}" today)))))

(deftest test-config
  (testing "defaults live in code, so every garden gets the suggestion"
    (is (= {:accession {:template "{year}.{seq:0000}" :strict? false}
            :material {:template "{seq}" :strict? false}}
           (ct.i/config {}))))

  (testing "stored values win, and \"1\" reads as strict"
    (is (= {:accession {:template "N{seq:0000}" :strict? true}
            :material {:template "{seq}" :strict? false}}
           (ct.i/config {"codes.accession_template" "N{seq:0000}"
                         "codes.accession_strict" "1"
                         "codes.material_strict" "0"})))))
