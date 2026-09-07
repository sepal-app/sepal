(ns sepal.app.routes.accession.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

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

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

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
