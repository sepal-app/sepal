(ns sepal.app.routes.material.create-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest test-create-material-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/material/new/"))
            create-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/material/new/"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token create-token
                                                          :code ""
                                                          :accession-id ""
                                                          :quantity "0"}))]
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

(deftest test-create-material-store-failure-answers-with-the-fallback
  (tf/testing "A POST that validates but fails in the store answers with the
               fallback, not an empty 422"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user location]}]
      ;; The accession id passes FormParams — it is an int above zero — and
      ;; then violates the foreign key, so the failure carries no malli
      ;; explain. Before failjure this humanized to nil and went out as a 422
      ;; with an empty body, which showed the user nothing.
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/material/new/"))
            create-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/material/new/"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token create-token
                                                          :code "M1"
                                                          :accession-id "999999"
                                                          :location-id (str (:location/id location))
                                                          :quantity "1"
                                                          :status "alive"
                                                          :type "plant"}))]
        (is (not= 422 (:status response))
            (str "A failure with no field errors must not answer 422. Body: "
                 (:body response)))

        ;; Not merely "a redirect": the success path redirects too, to the new
        ;; record. Only the fallback sends you back to the create form.
        (is (= "/material/new/" (get-in response [:headers "HX-Redirect"]))
            (str "Expected the fallback redirect to the create form, got "
                 (pr-str (get-in response [:headers "HX-Redirect"]))))))))

(deftest test-create-material-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/material/new/"))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#material-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-create-material-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/material/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#code-errors"))
            "Code field should have error container with id code-errors")))))

(deftest test-material-create-hints-the-intended-location
  (tf/testing "?accession-id= prefills the accession and names its intended location"
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
                                   (peri/request (str "/material/new/?accession-id="
                                                      (:accession/id accession))))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body (str "select#accession-id option[value=\""
                                           (:accession/id accession) "\"]")))
            "the accession should already be selected")
        (is (.contains (.text body) (:location/name location))
            "the form should name the accession's intended location")))))

(deftest test-material-create-without-an-accession-id-is-unchanged
  (tf/testing "no query parameter, no hint"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/material/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (nil? (.selectFirst body "select#accession-id option[value]"))
            "the accession select should have no preselected option")))))

(deftest test-no-reason-for-change-on-the-create-form
  (tf/testing "a record being made for the first time has not changed from
               anything. The create page passes no reasons, so the select
               offered None and nothing else."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/material/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (nil? (.selectFirst body "select#reason"))
            "the field is absent, not an empty picker")
        (is (not (.contains (.text body) "Reason for change")))))))

(deftest test-the-edit-form-still-asks-for-a-reason
  (tf/testing "there a change is exactly what is happening"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/location] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/location)}}
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/material/" (:material/id material)
                                                      "/general/")))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "select#reason")))
        (is (pos? (.size (.select body "select#reason option")))
            "and it offers more than nothing")))))
