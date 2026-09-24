(ns sepal.app.routes.code-templates-test
  "The code template feature end to end, through the routes a client uses."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.settings.interface :as settings.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- clear-codes! []
  (doseq [k ["codes.accession_template" "codes.material_template"
             "codes.accession_strict" "codes.material_strict"]]
    (settings.i/delete! *db* k)))

;; Settings rows outlive a test and the suite shares one database, so a strict
;; flag left behind here would start enforcing templates in every other
;; namespace's accession and material tests.
(use-fixtures :each (fn [t] (try (t) (finally (clear-codes!)))))

(def ^:private accession-template "ZT{year}-{seq:0000}")

(defn- set-codes! [settings]
  (settings.i/set-values! *db* settings))

(defn- editor-session []
  (let [password "testpassword123"
        email (str "editor-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password password :role :editor})
    (app.test/login email password)))

(defn- attr-value [body selector]
  (some-> (.selectFirst body selector) (.attr "value")))

(defn- text-of [body selector]
  (some-> (.selectFirst body selector) (.text)))

;; ---------------------------------------------------------------- prefill

(deftest test-accession-create-get-prefills-the-code
  (testing "the field arrives filled in, so you never look up the last number"
    (set-codes! {"codes.accession_template" accession-template
                 "codes.accession_strict" "0"})
    (let [sess (editor-session)
          {:keys [response]} (peri/request sess "/accession/new/")
          body (Jsoup/parse ^String (:body response))]
      (is (= 200 (:status response)))
      (is (re-matches #"ZT\d{4}-\d{4}" (attr-value body "#code"))
          "prefilled with the next code, not left empty")
      ;; The control is hand-rolled rather than built by ui.form/input-field,
      ;; so it has to carry the aria wiring input-field would have supplied.
      (is (= "code-description" (.attr (.selectFirst body "#code") "aria-describedby"))
          "the help text is announced with the field"))))

(deftest test-material-create-get-depends-on-the-accession
  (tf/testing "material cannot suggest a code until it knows the accession"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)
                                       :intended-location (ig/ref :key/location)}}
    (fn [{:keys [acc]}]
      (set-codes! {"codes.material_template" "{seq}"
                   "codes.material_strict" "0"})
      (let [sess (editor-session)]
        (testing "a bare /material/new has no accession, so the field is empty"
          (let [{:keys [response]} (peri/request sess "/material/new/")
                body (Jsoup/parse ^String (:body response))]
            (is (= 200 (:status response)))
            (is (str/blank? (attr-value body "#code")))
            (is (= "Choose an accession first"
                   (.attr (.selectFirst body "#code") "placeholder"))
                "the placeholder names the dependency")))

        (testing "arriving from \"Plant here\" the accession is known"
          (let [{:keys [response]} (peri/request
                                     sess (str "/material/new/?accession-id="
                                               (:accession/id acc)))
                body (Jsoup/parse ^String (:body response))]
            (is (= "1" (attr-value body "#code"))
                "an accession with no material starts at one")))))))

(deftest test-next-code-endpoint
  (tf/testing "GET /material/next-code returns that accession's next code"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}
     [::material.i/factory :key/m1] {:db *db*
                                     :accession (ig/ref :key/acc)
                                     :location (ig/ref :key/location)
                                     :data {:code "1"}}}
    (fn [{:keys [acc]}]
      (set-codes! {"codes.material_template" "{seq}"})
      (let [sess (editor-session)
            {:keys [response]} (peri/request
                                 sess (str "/material/next-code/?accession-id="
                                           (:accession/id acc)))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (= "2" (attr-value body "#code")))))))

(deftest test-the-code-follows-the-accession-picker
  (tf/testing "the create form refetches the code when the accession changes; the edit form does not"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}
     [::material.i/factory :key/m1] {:db *db*
                                     :accession (ig/ref :key/acc)
                                     :location (ig/ref :key/location)}}
    (fn [{:keys [m1]}]
      (let [sess (editor-session)
            page (fn [path] (Jsoup/parse ^String (:body (:response (peri/request sess path)))))
            trigger "input#code[hx-trigger='change from:#accession-id'][hx-include='#accession-id']"]
        (is (some? (.selectFirst (page "/material/new/") trigger)))
        (is (nil? (.selectFirst (page (str "/material/" (:material/id m1) "/general/"))
                                "input#code[hx-get]"))
            "an edit keeps the code it has")))))

(deftest test-the-next-code-button
  (tf/testing "a create form can ask for the current next code"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [_]
      (set-codes! {"codes.accession_template" accession-template})
      (let [sess (editor-session)]
        (testing "the button is on the create form"
          (let [{:keys [response]} (peri/request sess "/accession/new/")
                body (Jsoup/parse ^String (:body response))
                button (.selectFirst body "button[hx-get*=next-code]")]
            (is (some? button) "a refresh control beside the Code field")
            (is (= "#code" (.attr button "hx-target")))
            (is (= "Use the next available code" (.attr button "aria-label"))
                "an icon-only control needs a name")))

        (testing "and the endpoint answers with the Code field"
          (let [{:keys [response]} (peri/request sess "/accession/next-code/")
                body (Jsoup/parse ^String (:body response))]
            (is (= 200 (:status response)))
            (is (re-matches #"ZT\d{4}-\d{4}" (attr-value body "#code")))))))))

(deftest test-a-blank-template-suggests-nothing
  (tf/testing "a garden that turned the suggestion off"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [_]
      (set-codes! {"codes.accession_template" ""})
      (let [sess (editor-session)
            {:keys [response]} (peri/request sess "/accession/new/")
            body (Jsoup/parse ^String (:body response))]
        (is (str/blank? (attr-value body "#code"))
            "no prefill, rather than falling back to the built-in default")))))

;; ------------------------------------------------------------ enforcement

(defn- accession-params
  "Every field the form posts. FormParams is a closed map and only the flags
  are optional, so a missing key fails validation before any code check runs."
  [taxon-id code]
  {:code code
   :taxon-id (str taxon-id)
   :id-qualifier ""
   :id-qualifier-rank ""
   :provenance-type ""
   :wild-provenance-status ""
   :supplier-contact-id ""
   :intended-location-id ""
   :date-received ""
   :date-accessioned ""
   :received-type ""
   :quantity-received ""})

(defn- post-accession
  "Create an accession through the form, on top of a fresh token."
  [sess taxon-id code & [extra]]
  (let [{:keys [response] :as sess} (peri/request sess "/accession/new/")
        token (test.i/response-anti-forgery-token response)]
    (peri/request sess "/accession/new/"
                  :request-method :post
                  :params (merge (accession-params taxon-id code)
                                 {:__anti-forgery-token token}
                                 extra))))

(deftest test-strict-create-is-a-wall
  (tf/testing "a code of the wrong shape is refused, with an example"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (set-codes! {"codes.accession_template" accession-template
                   "codes.accession_strict" "1"})
      (let [{:keys [response]} (post-accession (editor-session)
                                               (:taxon/id taxon)
                                               "whatever-i-like")
            body (Jsoup/parse ^String (:body response))]
        (is (= 422 (:status response)))
        (is (re-find #"Code must look like ZT\d{4}-\d{4}"
                     (text-of body "#code-errors"))
            "the message shows the convention rather than describing it")))))

(deftest test-suggestion-create-accepts-any-shape
  (tf/testing "with strict off the template is a hint"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (set-codes! {"codes.accession_template" accession-template
                   "codes.accession_strict" "0"})
      (let [code (str "free-form-" (random-uuid))
            {:keys [response]} (post-accession (editor-session)
                                               (:taxon/id taxon)
                                               code)
            redirect (get-in response [:headers "HX-Redirect"])]
        (is (= 200 (:status response)))
        (is (some? redirect) "saved, and the client is sent to the new record")
        ;; This record was created through the form, so no factory owns it, and
        ;; the taxon fixture cannot be torn down while it points at one.
        (when redirect
          (accession.i/delete! *db* (parse-long (last (remove empty? (str/split redirect #"/"))))))))))

(deftest test-a-duplicate-code-is-refused-and-re-suggested
  (tf/testing "the unique index behind the suggestion"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)
                                       :data {:code "ZT2026-0777"}}}
    (fn [{:keys [taxon]}]
      (set-codes! {"codes.accession_template" accession-template
                   "codes.accession_strict" "0"})
      (let [{:keys [response]} (post-accession (editor-session)
                                               (:taxon/id taxon)
                                               "ZT2026-0777")
            body (Jsoup/parse ^String (:body response))]
        (is (= 422 (:status response)))
        (is (re-find #"ZT2026-0777 is already taken" (text-of body "#code-errors")))
        ;; Not the next suggestion. A failed save must not quietly change what
        ;; is on screen; the refresh control is how you ask for another.
        (is (= "ZT2026-0777" (attr-value body "#code"))
            "the field still holds what was submitted")
        ;; The swapped-in field is the one field in error, so it has to carry
        ;; the error state. aria-invalid is what draws its red border.
        (is (= "true" (.attr (.selectFirst body "#code") "aria-invalid"))
            "the replacement field still reads as invalid")
        (is (= "code-description code-errors"
               (.attr (.selectFirst body "#code") "aria-describedby"))
            "and still points at the message beside it")))))

;; ------------------------------------------------------------------- edit

(defn- post-accession-edit [sess id taxon-id code & [extra]]
  (let [path (str "/accession/" id "/general/")
        {:keys [response] :as sess} (peri/request sess path)
        token (test.i/response-anti-forgery-token response)]
    (peri/request sess path
                  :request-method :post
                  :params (merge (accession-params taxon-id code)
                                 {:__anti-forgery-token token}
                                 extra))))

(deftest test-strict-edit-is-a-speed-bump
  (tf/testing "an edit to a non-matching code asks, then lets you through"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)
                                       :data {:code "ZT2026-0500"}}}
    (fn [{:keys [acc taxon]}]
      (set-codes! {"codes.accession_template" accession-template
                   "codes.accession_strict" "1"})
      (let [id (:accession/id acc)
            sess (editor-session)
            {:keys [response]} (post-accession-edit sess id (:taxon/id taxon) "N0046A")
            body (Jsoup/parse ^String (:body response))]
        (is (= 422 (:status response)))
        (is (some? (.selectFirst body "#code-confirm"))
            "the confirmation is swapped in, not a bare refusal")
        (is (some? (.selectFirst body "input[name=code-override]"))
            "with a tickbox that the next submit will post"))

      (testing "ticking it and saving again goes through"
        (let [id (:accession/id acc)
              {:keys [response]} (post-accession-edit (editor-session) id
                                                      (:taxon/id taxon) "N0046A"
                                                      {:code-override "1"})]
          (is (= 200 (:status response)))
          (is (some? (get-in response [:headers "HX-Redirect"])))
          (is (= "N0046A" (:accession/code (accession.i/get-by-id *db* id)))))))))

(deftest test-an-untouched-legacy-code-saves-without-confirming
  (tf/testing "the rule that matters more than it looks"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)
                                       :data {:code "N0046"}}}
    (fn [{:keys [acc taxon]}]
      ;; N0046 predates any template this garden set. Editing something else on
      ;; the record must not make you confirm a code you never touched --
      ;; otherwise every save on every legacy record grows a step, and the
      ;; feature becomes something gardens turn off.
      (set-codes! {"codes.accession_template" accession-template
                   "codes.accession_strict" "1"})
      (let [id (:accession/id acc)
            {:keys [response]} (post-accession-edit (editor-session) id
                                                    (:taxon/id taxon) "N0046"
                                                    {:quantity-received "7"})]
        (is (= 200 (:status response))
            "no confirmation, because the code is unchanged")
        (is (some? (get-in response [:headers "HX-Redirect"])))))))
