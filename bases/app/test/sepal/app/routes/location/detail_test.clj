(ns sepal.app.routes.location.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
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

(def test-location-data
  {:name "Test Location"
   :code "LOC-001"
   :description ""})

(deftest test-update-location-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [location (location.i/create! *db* test-location-data)
            sess (app.test/login (:user/email user) "testpassword123")
            detail-url (str "/location/" (:location/id location) "/")
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

(deftest test-update-location-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [location (location.i/create! *db* test-location-data)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/location/" (:location/id location) "/")))
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#location-form")]
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-update-location-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [location (location.i/create! *db* test-location-data)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/location/" (:location/id location) "/")))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "#name-errors"))
            "Name field should have error container with id name-errors")))))

(deftest test-location-detail-shows-moved-material
  (tf/testing "material that moved away appears in the location's Moved section"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db*
                                             :taxon (ig/ref :key/taxon)}
     [::location.i/factory :key/loc1] {:db *db*}
     [::location.i/factory :key/loc2] {:db *db*}
     [::material.i/factory :key/material] {:db *db*
                                           :accession (ig/ref :key/accession)
                                           :location (ig/ref :key/loc1)}}
    (fn [{:keys [user material loc1 loc2]}]
      (material.i/update! *db* (:material/id material)
                          {:location-id (:location/id loc2)
                           :reason "transferred"})
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/location/" (:location/id loc1) "/")))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body ":containsOwn(Moved)"))
            "the panel should have a Moved section")
        (is (.contains (.text body) (:material/code material))
            "the moved material's code should appear")
        (jdbc.sql/delete! *db* :material {:id (:material/id material)})))))
