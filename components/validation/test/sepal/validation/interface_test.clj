(ns sepal.validation.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.generator :as mg]
            [sepal.error.interface :as error.i]
            [sepal.validation.interface :as validation.i]))

(deftest parse-date-test
  (testing "valid ISO-8601 date returns the date string"
    (is (= "2024-01-15" (validation.i/parse-date "2024-01-15")))
    (is (= "2024-12-31" (validation.i/parse-date "2024-12-31"))))

  (testing "empty string returns nil"
    (is (nil? (validation.i/parse-date ""))))

  (testing "nil returns nil"
    (is (nil? (validation.i/parse-date nil))))

  (testing "invalid format returns ::invalid-date"
    (is (= ::validation.i/invalid-date (validation.i/parse-date "not-a-date")))
    (is (= ::validation.i/invalid-date (validation.i/parse-date "01-15-2024")))
    (is (= ::validation.i/invalid-date (validation.i/parse-date "2024/01/15"))))

  (testing "impossible dates return ::invalid-date"
    (is (= ::validation.i/invalid-date (validation.i/parse-date "2024-02-30")))
    (is (= ::validation.i/invalid-date (validation.i/parse-date "2024-13-01")))))

(deftest date-schema-test
  (testing "valid date passes validation"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d "2024-01-15"})]
      (is (not (error.i/error? result)))
      (is (= {:d "2024-01-15"} result))))

  (testing "empty string becomes nil"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d ""})]
      (is (not (error.i/error? result)))
      (is (= {:d nil} result))))

  (testing "invalid date returns error"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d "bad-date"})]
      (is (error.i/error? result))
      (is (= {:d ["must be a valid date (YYYY-MM-DD)"]}
             (validation.i/humanize result)))))

  (testing "impossible date returns error"
    (let [schema [:map [:d [:maybe validation.i/date]]]
          result (validation.i/validate-form-values schema {:d "2024-02-30"})]
      (is (error.i/error? result)))))

;; =============================================================================
;; email-re
;; =============================================================================

(def ^:private html-email-re
  "The format a browser enforces for `<input type=email>`. email-re must stay
  inside it."
  #"^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$")

(deftest email-re-accepts-ordinary-addresses
  (doseq [address ["someone@example.com"
                   "first.last@example.co.uk"
                   "user+tag@example.org"
                   "u_n-a.m%e@sub.domain.example.com"
                   "x@a.io"]]
    (is (re-matches validation.i/email-re address)
        (format "%s is a real address and must be accepted" address))))

(deftest email-re-rejects-what-a-browser-will-not-submit
  (testing "domain shapes Chromium refuses to submit, so a user created with one
            could never log in"
    (doseq [address ["x@-bar.com"
                     "x@bar-.com"
                     "x@a..b.com"
                     "x@.bar.com"]]
      (is (nil? (re-matches validation.i/email-re address))
          (format "%s must be rejected — a browser will not submit it" address))))

  (testing "the obvious nonsense"
    (doseq [address ["" "nobody" "no@at" "@example.com" "spaces in@example.com"]]
      (is (nil? (re-matches validation.i/email-re address))
          (format "%s is not an address" address)))))

(deftest every-generated-address-is-one-a-browser-would-submit
  (testing "fixtures generate from this regex. While it was wider than the HTML
            format, 6.2% of draws were addresses Chromium refused, hanging the
            e2e login test until it timed out — one CI run in six"
    ;; 200 draws, not 2000: generating from a regex costs ~3ms each, and 200 is
    ;; already >99% likely to catch a defect as rare as 3% of addresses.
    (let [addresses (repeatedly 200 #(mg/generate validation.i/email-re))
          rejected (remove #(re-matches html-email-re %) addresses)]
      (is (empty? rejected)
          (format "%d of 200 generated addresses would not submit, e.g. %s"
                  (count rejected)
                  (pr-str (vec (take 5 rejected))))))))
