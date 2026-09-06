(ns sepal.app.routes.taxon.detail-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest test-update-taxon-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            detail-url (str "/taxon/" (:taxon/id taxon) "/name/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request detail-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request detail-url
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :name ""
                                                          :author ""
                                                          :rank ""}))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response) " with body: " (:body response)))

        (is (= "text/html" (get-in response [:headers "Content-Type"]))
            "Should return text/html content type for HTMX OOB swap")

        (let [body (Jsoup/parse ^String (:body response))]
          (let [oob-elements (.select body "[hx-swap-oob]")]
            (is (pos? (.size oob-elements))
                "Should have elements with hx-swap-oob attribute"))

          (is (some? (.selectFirst body "#name-errors"))
              "Should have error list for name field"))))))

(deftest test-update-taxon-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/taxon/" (:taxon/id taxon) "/name/")))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#taxon-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-update-taxon-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/taxon/" (:taxon/id taxon) "/name/")))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#name-errors"))
            "Name field should have error container with id name-errors")))))

(deftest test-set-and-clear-distribution-through-the-form
  (tf/testing "distribution round-trips through the taxon edit route"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      ;; A successful POST logs an activity row against this ephemeral user.
      ;; activity.created_by deliberately has no ON DELETE CASCADE, so this
      ;; cleanup is a concession to the user factory's hard-delete teardown,
      ;; not a workaround for a missing constraint. Same as
      ;; create-test/test-create-a-cultivar-through-the-form.
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              detail-url (str "/taxon/" (:taxon/id taxon) "/name/")
              base-params {:name (:taxon/name taxon)
                           :author (or (:taxon/author taxon) "")
                           :rank (name (:taxon/rank taxon))
                           :parent-id ""}]
          (testing "setting it"
            (let [{:keys [response] :as sess} (-> sess (peri/request detail-url))
                  token (test.i/response-anti-forgery-token response)
                  {:keys [response]} (-> sess
                                         (peri/request detail-url
                                                       :request-method :post
                                                       :params (assoc base-params
                                                                      :__anti-forgery-token token
                                                                      :distribution "Central America")))]
              (is (contains? #{200 204 302} (:status response))
                  (str "expected a redirect or success, got " (:status response)
                       " with body: " (:body response)))
              (is (= "Central America" (:taxon/distribution (taxon.i/get-by-id *db* (:taxon/id taxon))))
                  "distribution should be saved")

              (let [{:keys [response]} (-> sess (peri/request detail-url))
                    body (Jsoup/parse ^String (:body response))
                    field (.selectFirst body "input#distribution")]
                (is (= "Central America" (.val field))
                    "the saved value should render back into the form"))))

          (testing "clearing it"
            (let [{:keys [response] :as sess} (-> sess (peri/request detail-url))
                  token (test.i/response-anti-forgery-token response)
                  {:keys [response]} (-> sess
                                         (peri/request detail-url
                                                       :request-method :post
                                                       :params (assoc base-params
                                                                      :__anti-forgery-token token
                                                                      :distribution "")))]
              (is (contains? #{200 204 302} (:status response))
                  (str "expected a redirect or success, got " (:status response)
                       " with body: " (:body response)))
              (is (nil? (:taxon/distribution (taxon.i/get-by-id *db* (:taxon/id taxon))))
                  "distribution should be cleared to nil")

              (let [{:keys [response]} (-> sess (peri/request detail-url))
                    body (Jsoup/parse ^String (:body response))
                    field (.selectFirst body "input#distribution")]
                (is (str/blank? (.val field))
                    "the cleared value should render as empty")))))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))
