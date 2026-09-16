(ns sepal.app.routes.location.create-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest test-create-location-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/location/new/"))
            create-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/location/new/"
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

(deftest test-create-location-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/location/new/"))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#location-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-create-location-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/location/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#name-errors"))
            "Name field should have error container with id name-errors")))))

(deftest test-a-second-location-cannot-take-a-code-already-in-use
  ;; Nothing refused a duplicate code, so a location saved twice simply became
  ;; two rows with the same code and name.
  (tf/testing "POST with a code another location already has"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [existing (location.i/create! *db* {:code "DUPE" :name "First bed"})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess "/location/new/")
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess "/location/new/"
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :name "Second bed"
                                                      :code "DUPE"
                                                      :description ""})]
        (try
          (is (= 422 (:status response))
              (str "Expected 422, got " (:status response)))
          (is (re-find #"already taken" (:body response))
              "the field says which code is taken rather than reloading the page")
          (is (= 1 (count (db.i/execute! *db* {:select [:id] :from [:location]
                                               :where [:= :code "DUPE"]})))
              "and no second row was written")
          (finally
            (location.i/delete! *db* (:location/id existing))))))))

