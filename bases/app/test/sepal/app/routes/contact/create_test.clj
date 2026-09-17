(ns sepal.app.routes.contact.create-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
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

(def valid-create-params
  "The full field set a browser submits, with empty strings for blank fields."
  {:name "Fairchild Tropical Gardens"
   :business ""
   :type "botanic_garden"
   :email ""
   ;; No :address. The form renders that one only for a contact saved before
   ;; the split, and neither of these has one, so a browser posts no such field.
   :address1 ""
   :address2 ""
   :city ""
   :province ""
   :postal-code ""
   :country ""
   :phone ""
   :notes ""})

(deftest test-create-contact-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/contact/new/"))
            create-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/contact/new/"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token create-token
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

(deftest test-create-contact-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/contact/new/"))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#contact-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-create-contact-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/contact/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#name-errors"))
            "Name field should have error container with id name-errors")))))

(deftest test-create-contact-with-type
  (tf/testing "POST with a type saves it as the enum keyword"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess
                                              (peri/request "/contact/new/"))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request "/contact/new/"
                                                   :request-method :post
                                                   :params (assoc valid-create-params
                                                                  :__anti-forgery-token token)))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (let [redirect (get-in response [:headers "HX-Redirect"])
                id (parse-long (last (remove empty? (str/split redirect #"/"))))
                contact (contact.i/get-by-id *db* id)]
            (is (= :botanic_garden (:contact/type contact)))
            (is (= "Fairchild Tropical Gardens" (:contact/name contact)))))
        (finally
          ;; Creating a contact writes an activity row referencing the factory
          ;; user, and the user factory's teardown hard-deletes it (the only
          ;; hard delete in the codebase) -- clean up so the FK lets it.
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-create-contact-rejects-invalid-type
  (tf/testing "POST with a type outside the vocabulary returns 422"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/contact/new/"))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/contact/new/"
                                                 :request-method :post
                                                 :params (assoc valid-create-params
                                                                :__anti-forgery-token token
                                                                :type "not_a_type")))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response)))
        (let [body (Jsoup/parse ^String (:body response))]
          (is (some? (.selectFirst body "#type-errors"))
              "type should have a field error"))))))

(deftest test-create-contact-with-split-address
  (tf/testing "POST saves address1, address2 and city as their own fields"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess (peri/request "/contact/new/"))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request "/contact/new/"
                                                   :request-method :post
                                                   :params (assoc valid-create-params
                                                                  :address1 "10901 Old Cutler Road"
                                                                  :address2 "Attn: Herbarium"
                                                                  :city "Coral Gables"
                                                                  :__anti-forgery-token token)))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (let [redirect (get-in response [:headers "HX-Redirect"])
                id (parse-long (last (remove empty? (str/split redirect #"/"))))
                contact (contact.i/get-by-id *db* id)]
            (is (= "10901 Old Cutler Road" (:contact/address1 contact)))
            (is (= "Attn: Herbarium" (:contact/address2 contact)))
            (is (= "Coral Gables" (:contact/city contact)))
            (testing "and the legacy one-line address stays empty, because
                      nothing parses a split out of what was never typed there"
              (is (nil? (:contact/address contact))))))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-the-legacy-address-field-appears-only-for-a-contact-that-has-one
  (tf/testing "a new contact's form offers no one-line Address input"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/contact/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (nil? (.selectFirst body "input[name=address]"))
            "nothing should offer the field the split replaced")
        (doseq [field ["address1" "address2" "city"]]
          (is (some? (.selectFirst body (format "input[name=%s]" field)))
              (str "the form should offer " field)))))))
