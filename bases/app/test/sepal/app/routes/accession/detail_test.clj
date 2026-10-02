(ns sepal.app.routes.accession.detail-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.codes :as codes]
            [sepal.app.routes.accession.detail.general :as detail-general]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.app.ui.form :as ui.form]
            [sepal.error.interface :as err.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as mat.i]
            [sepal.settings.interface :as settings.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

;; Settings rows outlive a test and the suite shares one database.
(use-fixtures :each (fn [t] (try (t) (finally (app.test/reset-codes! *db*)))))

(defn- in-place-fixtures []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}})

(defn- post-general
  "Posts the general form for `accession` the way htmx does, with `overrides`
  on top of a form that changes nothing."
  [user accession overrides]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        url (str "/accession/" (:accession/id accession) "/general/")
        {:keys [response] :as sess} (peri/request sess url)
        token (test.i/response-anti-forgery-token response)]
    (:response (peri/request sess url
                             :request-method :post
                             :headers {"hx-request" "true"}
                             :params (merge {:__anti-forgery-token token
                                             :code (:accession/code accession)
                                             :taxon-id (str (:accession/taxon-id accession))
                                             :id-qualifier ""
                                             :id-qualifier-rank ""
                                             :provenance-type ""
                                             :wild-provenance-status ""
                                             :supplier-contact-id ""
                                             :intended-location-id ""
                                             :date-received ""
                                             :date-accessioned ""
                                             :received-type ""
                                             :quantity-received ""}
                                            overrides)))))

(deftest test-update-accession-general-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            detail-url (str "/accession/" (:accession/id accession) "/general/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request detail-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request detail-url
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :code ""
                                                          :taxon-id ""}))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response) " with body: " (:body response)))

        (is (= "text/html" (get-in response [:headers "Content-Type"]))
            "Should return text/html content type for HTMX OOB swap")

        (let [body (Jsoup/parse ^String (:body response))]
          (let [oob-elements (.select body "[hx-swap-oob]")]
            (is (pos? (.size oob-elements))
                "Should have elements with hx-swap-oob attribute"))

          (is (some? (.selectFirst body "#code-errors"))
              "Should have error list for code field"))))))

(deftest test-update-accession-general-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/accession/" (:accession/id accession) "/general/")))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#accession-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "morph" (.attr form "hx-swap"))
            "Form should morph the page in place")))))

(deftest test-update-accession-general-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/accession/" (:accession/id accession) "/general/")))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#code-errors"))
            "Code field should have error container with id code-errors")))))

(deftest test-update-accession-general-sets-the-intended-location
  (tf/testing "POST to the general tab saves the intended location"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession location]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              detail-url (str "/accession/" (:accession/id accession) "/general/")
              {:keys [response] :as sess} (-> sess (peri/request detail-url))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request detail-url
                                                   :request-method :post
                                                   :params {:__anti-forgery-token token
                                                            :code (:accession/code accession)
                                                            :taxon-id (str (:accession/taxon-id accession))
                                                            :id-qualifier ""
                                                            :id-qualifier-rank ""
                                                            :provenance-type ""
                                                            :wild-provenance-status ""
                                                            :supplier-contact-id ""
                                                            :date-received ""
                                                            :date-accessioned ""
                                                            :received-type ""
                                                            :quantity-received ""
                                                            :intended-location-id (str (:location/id location))}))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (is (= (:location/id location)
                 (:accession/intended-location-id
                   (accession.i/get-by-id *db* (:accession/id accession))))))
        (finally
          ;; The accession factory's teardown runs before the location's, but
          ;; the accession must stop naming the location first.
          (accession.i/update! *db* (:accession/id accession)
                               {:intended-location-id nil})
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-general-tab-preselects-the-saved-intended-location
  (tf/testing "the saved location is the selected option, not just present"
    ;; The select carries an empty placeholder option first, so a value option
    ;; without `selected` loses to it: the browser selects the first option and
    ;; the field renders blank after a save.
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db*
                                             :taxon (ig/ref :key/taxon)
                                             :intended-location (ig/ref :key/location)}}
    (fn [{:keys [user accession location]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/accession/"
                                                      (:accession/id accession)
                                                      "/general/")))
            body (Jsoup/parse ^String (:body response))
            picker (.selectFirst body "sepal-combobox#intended-location-id")]
        (is (some? picker) "the edit form should have the location picker")
        (is (= (str (:location/id location)) (.attr picker "data-value"))
            "the saved location should be the one chosen")
        (is (not (str/blank? (.attr picker "data-text")))
            "and it should arrive with a label, so the field is not blank on a
             record that has one — saving that blank erased it before")))))

(deftest test-accession-panel-shows-the-intended-location
  (tf/testing "the panel names the location and links to it"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db*
                                             :taxon (ig/ref :key/taxon)
                                             :intended-location (ig/ref :key/location)}}
    (fn [{:keys [user accession location]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/accession/"
                                                      (:accession/id accession)
                                                      "/panel/")))
            body (Jsoup/parse ^String (:body response))
            link (.selectFirst body (str "a[href=\"/location/"
                                         (:location/id location)
                                         "/\"]"))]
        (is (some? link) "the panel should link to the intended location")
        (is (= (:location/name location) (.text link)))))))

(deftest test-update-accession-general-saves-receipt-fields
  (tf/testing "the general tab sets both receipt fields"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession taxon]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              detail-url (str "/accession/" (:accession/id accession) "/general/")
              {:keys [response] :as sess} (-> sess (peri/request detail-url))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request detail-url
                                                   :request-method :post
                                                   :params {:__anti-forgery-token token
                                                            :code (:accession/code accession)
                                                            :taxon-id (str (:taxon/id taxon))
                                                            :id-qualifier ""
                                                            :id-qualifier-rank ""
                                                            :provenance-type ""
                                                            :wild-provenance-status ""
                                                            :supplier-contact-id ""
                                                            :intended-location-id ""
                                                            :date-received ""
                                                            :date-accessioned ""
                                                            :received-type "scion"
                                                            :quantity-received "0"}))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (let [saved (accession.i/get-by-id *db* (:accession/id accession))]
            (is (= :scion (:accession/received-type saved)))
            (is (= 0 (:accession/quantity-received saved))
                "zero is saved, not treated as blank")))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-received-type-and-material-type-are-independent
  (tf/testing "an accession received as seed can hold material that is a plant.
               One records what arrived, the other what the garden holds now,
               and neither is wrong -- the property that justifies two
               overlapping vocabularies."
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}
     [::accession.i/factory :key/accession]
     {:db *db* :taxon (ig/ref :key/taxon) :data {:received-type :seed}}}
    (fn [{:keys [accession location]}]
      (let [material (mat.i/create! *db* {:code "IND-1"
                                          :accession-id (:accession/id accession)
                                          :location-id (:location/id location)
                                          :type :plant
                                          :status :alive
                                          :quantity 1})]
        (try
          (is (not (err.i/error? material)) (err.i/data material))
          (is (= :seed (:accession/received-type accession)))
          (is (= :plant (:material/type material)))
          (finally
            (jdbc.sql/delete! *db* :material {:id (:material/id material)})))))))

(deftest test-a-save-says-so
  (tf/testing "the page a save answers with carries the banner, because a save
               that looks like it did nothing is indistinguishable from one
               that failed"
    (in-place-fixtures)
    (fn [{:keys [user accession]}]
      (try
        (let [response (post-general user accession {:quantity-received "3"})]
          (is (app.test/saved-in-place? response))
          (is (.contains (.text (Jsoup/parse ^String (:body response)))
                         "Accession updated successfully")))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-update-accession-general-rejects-a-future-date
  (tf/testing "a future accessioned date is a field error and nothing is saved"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            detail-url (str "/accession/" (:accession/id accession) "/general/")
            {:keys [response] :as sess} (-> sess (peri/request detail-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request detail-url
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :code (:accession/code accession)
                                                          :taxon-id (str (:taxon/id taxon))
                                                          :id-qualifier ""
                                                          :id-qualifier-rank ""
                                                          :provenance-type ""
                                                          :wild-provenance-status ""
                                                          :supplier-contact-id ""
                                                          :intended-location-id ""
                                                          :date-received ""
                                                          :date-accessioned "2999-01-01"
                                                          :received-type ""
                                                          :quantity-received ""}))]
        (is (= 422 (:status response)))
        ;; The factory generates the date, so compare with what it made.
        (is (= (:accession/date-accessioned accession)
               (:accession/date-accessioned
                 (accession.i/get-by-id *db* (:accession/id accession))))
            "the saved date is unchanged")))))

(deftest test-an-accession-save-answers-with-the-page
  (tf/testing "the page comes back with the saved code in every place it shows"
    (in-place-fixtures)
    (fn [{:keys [user accession]}]
      (try
        (let [response (post-general user accession {:code "NEW-CODE"})
              body (Jsoup/parse ^String (:body response))]
          (is (app.test/saved-in-place? response))
          (is (str/includes? (.text (.selectFirst body ".spl-crumbs-current")) "NEW-CODE")
              "the breadcrumb")
          (is (str/includes? (.text (.selectFirst body ".spl-record")) "NEW-CODE")
              "the record header")
          (is (str/includes? (.text (.selectFirst body "title")) "NEW-CODE")
              "the browser tab")
          (is (= "NEW-CODE" (.attr (.selectFirst body "input[name=code]") "value"))
              "the form, from the saved record"))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-a-code-the-template-rejects-asks-for-confirmation
  (tf/testing "422 with the confirmation and no page"
    (in-place-fixtures)
    (fn [{:keys [user accession]}]
      (settings.i/set-values! *db* {"codes.accession_template" "ZT{year}-{seq:0000}"
                                    "codes.accession_strict" "1"})
      (let [response (post-general user accession {:code "NOT-A-MATCH"})
            body (:body response)]
        (is (= 422 (:status response)))
        (is (some? (.selectFirst (Jsoup/parse ^String body)
                                 (str "#" codes/confirm-target-id)))
            "the confirmation")
        (is (not (str/includes? body "page-region")))))))

(deftest test-a-future-date-received-is-a-field-error
  (tf/testing "422 with the field's errors and no page"
    (in-place-fixtures)
    (fn [{:keys [user accession]}]
      (let [response (post-general user accession {:date-received "2999-01-01"})
            body (:body response)]
        (is (= 422 (:status response)))
        (is (some? (.selectFirst (Jsoup/parse ^String body)
                                 (str "#" (ui.form/errors-id "date-received")))))
        (is (not (str/includes? body "page-region")))))))

(deftest test-an-accession-save-that-throws-answers-with-a-flash
  (tf/testing "422 with the error flash and no page"
    (in-place-fixtures)
    (fn [{:keys [user accession]}]
      (with-redefs [detail-general/save! (fn [& _] (throw (ex-info "disk full" {})))]
        (let [response (post-general user accession {:code "NEW-CODE"})]
          (is (= 422 (:status response)))
          (is (not (str/includes? (str (:body response)) "page-region")))
          (is (str/includes? (str (:body response)) "Could not save the accession")))))))
