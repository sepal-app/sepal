(ns sepal.app.routes.settings.codes-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.settings.interface :as settings.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- flash-banner-text [body]
  (some-> (.selectFirst body ".banner-text") (.text)))

(defn- admin-session []
  (let [password "testpassword123"
        email (str "admin-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password password :role :admin})
    (app.test/login email password)))

(defn- post-codes
  "GET the page for a token, then POST `params` on top of the anti-forgery
  field."
  [params]
  (let [sess (admin-session)
        {:keys [response] :as sess} (peri/request sess "/settings/codes")
        token (test.i/response-anti-forgery-token response)]
    (peri/request sess "/settings/codes"
                  :request-method :post
                  :params (merge {:__anti-forgery-token token} params))))

(defn- clear-codes! []
  ;; Tests share one database and run in random order, so the absent-rows path
  ;; only exists if this test makes it.
  (doseq [k ["codes.accession_template" "codes.material_template"
             "codes.accession_strict" "codes.material_strict"]]
    (settings.i/delete! *db* k)))

(deftest test-the-page-renders-with-the-defaults
  (testing "GET /settings/codes shows the default templates and the next code"
    (clear-codes!)
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/codes")]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))
            template (.selectFirst body "#accession_template")]
        (is (some? template) "the accession template field is on the page")
        (is (= "{year}.{seq:0000}" (.attr template "value"))
            "the default comes from code, not from a seeded settings row")
        (is (re-find #"Next: \d{4}\.\d{4}"
                     (.text (.selectFirst body ".spl-settings-content")))
            "the field says what it would generate right now")

        ;; spl-checkbox sizes the box at 16px square. Put it on the wrapping
        ;; label instead and the label is 16px wide, so its text wraps one word
        ;; per line and overflows the section below it.
        (is (some? (.selectFirst body "input#accession_strict.spl-checkbox"))
            "the checkbox class is on the input")
        (is (nil? (.selectFirst body "label.spl-checkbox"))
            "and not on a label")))))

(deftest test-saving-both-templates-and-both-flags
  (testing "POST /settings/codes stores the four rows"
    (let [{:keys [response] :as sess} (post-codes {:accession_template "ZZ{year}-{seq:0000}"
                                                   :material_template "{seq}"
                                                   :accession_strict "1"})]
      (is (= 303 (:status response)))
      (let [{:keys [response]} (peri/follow-redirect sess)
            body (Jsoup/parse ^String (:body response))]
        (is (= "Code settings updated successfully" (flash-banner-text body))))

      (let [stored (settings.i/get-values *db* "codes")]
        (is (= "ZZ{year}-{seq:0000}" (get stored "codes.accession_template")))
        (is (= "{seq}" (get stored "codes.material_template")))
        (is (= "1" (get stored "codes.accession_strict")))
        (is (= "0" (get stored "codes.material_strict"))
            "an unticked checkbox posts nothing and is stored as off")))))

(deftest test-the-enforcement-checkbox-has-one-label
  (testing "not form/field's label as well as its own"
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/codes")
          body (Jsoup/parse ^String (:body response))]
      ;; Two labels make the accessible name "Enforcement Reject a code that
      ;; does not fit", and clicking the heading toggles the box.
      (is (zero? (.size (.select body "label[for=accession_strict]")))
          "no second label pointing at the checkbox")
      (is (= 1 (.size (.select body "input#accession_strict")))))))

(deftest test-a-blank-template-can-be-set-and-sticks
  (testing "clearing a template turns the suggestion off for good"
    (let [{:keys [response]} (post-codes {:accession_template ""
                                          :material_template ""})]
      (is (= 303 (:status response)) "a blank template is a valid setting"))

    (let [stored (settings.i/get-values *db* "codes")]
      (is (= "" (get stored "codes.accession_template"))
          "stored as empty, not absent -- an absent row would take the default"))

    (testing "and the page comes back blank rather than showing the default"
      (let [sess (admin-session)
            {:keys [response]} (peri/request sess "/settings/codes")
            body (Jsoup/parse ^String (:body response))]
        (is (str/blank? (.attr (.selectFirst body "#accession_template") "value")))))))

(deftest test-an-invalid-template-is-rejected
  (testing "a template with no seq token comes back as a field error"
    ;; The suite shares one database and settings rows outlive a test, so
    ;; "nothing was stored" means unchanged, not absent.
    (let [before (get (settings.i/get-values *db* "codes") "codes.accession_template")
          {:keys [response]} (post-codes {:accession_template "{year}"
                                          :material_template "{seq}"})]
      (is (= 200 (:status response))
          "the page re-renders rather than redirecting")
      (let [body (Jsoup/parse ^String (:body response))]
        (is (re-find #"(?i)seq"
                     (.text (.selectFirst body "#accession_template-errors")))
            "the error names the missing token"))
      (is (= before (get (settings.i/get-values *db* "codes") "codes.accession_template"))
          "the rejected template was not stored"))))

(deftest test-strict-with-a-blank-template-is-rejected
  (testing "enforcing nothing, loudly, is refused"
    (let [{:keys [response]} (post-codes {:accession_template ""
                                          :material_template "{seq}"
                                          :accession_strict "1"})]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (re-find #"(?i)set a template"
                     (.text (.selectFirst body "#accession_template-errors"))))))))
