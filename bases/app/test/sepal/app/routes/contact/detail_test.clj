(ns sepal.app.routes.contact.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def test-contact-data
  {:name "Test Contact"
   :business ""
   :notes ""})

(def valid-update-params
  "The full field set a browser submits, with empty strings for blank fields."
  {:name "Test Contact"
   :business ""
   :type "expedition"
   :email ""
   :address ""
   :province ""
   :postal-code ""
   :country ""
   :phone ""
   :notes ""})

(deftest test-update-contact-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [contact (contact.i/create! *db* test-contact-data)
            sess (app.test/login (:user/email user) "testpassword123")
            detail-url (str "/contact/" (:contact/id contact) "/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request detail-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request detail-url
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :name ""}))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response) " with body: " (:body response)))

        (is (= "text/html" (get-in response [:headers "Content-Type"]))
            "Should return text/html content type for HTMX OOB swap")

        (let [body (Jsoup/parse ^String (:body response))]
          (let [oob-elements (.select body "[hx-swap-oob]")]
            (is (pos? (.size oob-elements))
                "Should have elements with hx-swap-oob attribute"))

          (is (some? (.selectFirst body "#name-errors"))
              "Should have error list for name field")

          (let [name-errors (.select body "#name-errors li")]
            (is (pos? (.size name-errors))
                "Name errors list should have error messages")))))))

(deftest test-update-contact-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [contact (contact.i/create! *db* test-contact-data)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/contact/" (:contact/id contact) "/")))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#contact-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-update-contact-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [contact (contact.i/create! *db* test-contact-data)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/contact/" (:contact/id contact) "/")))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#name-errors"))
            "Name field should have error container with id name-errors")))))

(deftest test-update-contact-type
  (tf/testing "POST with a type saves it as the enum keyword"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (try
        (let [contact (contact.i/create! *db* test-contact-data)
              sess (app.test/login (:user/email user) "testpassword123")
              detail-url (str "/contact/" (:contact/id contact) "/")
              {:keys [response] :as sess} (-> sess
                                              (peri/request detail-url))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request detail-url
                                                   :request-method :post
                                                   :params (assoc valid-update-params
                                                                  :__anti-forgery-token token)))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (is (= :expedition (:contact/type (contact.i/get-by-id *db* (:contact/id contact))))))
        (finally
          ;; Updating a contact writes an activity row referencing the factory
          ;; user, and the user factory's teardown hard-deletes it (the only
          ;; hard delete in the codebase) -- clean up so the FK lets it.
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-update-contact-form-shows-current-type
  (tf/testing "GET renders the select with the stored type selected"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [contact (contact.i/create! *db* (assoc test-contact-data :type :research_station))
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/contact/" (:contact/id contact) "/")))
            body (Jsoup/parse ^String (:body response))
            select (.selectFirst body "select[name=\"type\"]")]
        (is (some? select) "the type select should render")
        (let [selected (->> (.select select "option[selected]") first)]
          (is (some? selected) "an option should be selected")
          (is (= "research_station" (.attr selected "value"))))))))
