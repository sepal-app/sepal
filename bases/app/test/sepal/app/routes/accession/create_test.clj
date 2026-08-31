(ns sepal.app.routes.accession.create-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.routes.accession.create :as create]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest test-create-accession-validation-errors
  (tf/testing "POST with invalid data returns 422 with OOB error elements"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            ;; Get the create accession page to get a fresh CSRF token
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/accession/new/"))
            create-token (test.i/response-anti-forgery-token response)
            ;; POST with invalid data (empty code, invalid taxon-id)
            {:keys [response]} (-> sess
                                   (peri/request "/accession/new/"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token create-token
                                                          :code ""
                                                          :taxon-id "invalid"}))]
        ;; Should return 422 Unprocessable Entity
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response) " with body: " (:body response)))

        ;; Should have correct Content-Type for HTMX to process
        (is (= "text/html" (get-in response [:headers "Content-Type"]))
            "Should return text/html content type for HTMX OOB swap")

        ;; Parse the HTML response - should contain OOB error elements
        (let [body (Jsoup/parse ^String (:body response))]
          ;; Should have OOB error elements with hx-swap-oob attribute
          (let [oob-elements (.select body "[hx-swap-oob]")]
            (is (pos? (.size oob-elements))
                "Should have elements with hx-swap-oob attribute"))

          ;; Should have error lists for the invalid fields
          (is (some? (.selectFirst body "#code-errors"))
              "Should have error list for code field")

          (is (some? (.selectFirst body "#taxon-id-errors"))
              "Should have error list for taxon-id field")

          ;; Error lists should contain error messages
          (let [code-errors (.select body "#code-errors li")]
            (is (pos? (.size code-errors))
                "Code errors list should have error messages")))))))

(deftest test-create-accession-form-has-htmx-attributes
  (tf/testing "Form has HTMX attributes for OOB error swapping"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")

            ;; Get the create accession page
            {:keys [response]} (-> sess
                                   (peri/request "/accession/new/"))
            ;; Parse and check form attributes
            body (Jsoup/parse ^String (:body response))
            form (.selectFirst body "form#accession-form")]
        ;; Form should have hx-post for HTMX handling
        (is (some? (.attr form "hx-post"))
            "Form should have hx-post attribute")

        ;; Form should have hx-swap="none" for OOB-only updates
        (is (= "none" (.attr form "hx-swap"))
            "Form should have hx-swap='none' for OOB error updates")))))

(deftest test-create-accession-form-has-error-containers
  (tf/testing "Form fields have error containers with correct IDs for OOB targeting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/accession/new/"))
            ;; Parse and check error container IDs
            body (Jsoup/parse ^String (:body response))]
        ;; Each field should have an error container with the pattern {name}-errors
        (is (some? (.selectFirst body "#code-errors"))
            "Code field should have error container with id code-errors")

        (is (some? (.selectFirst body "#taxon-id-errors"))
            "Taxon field should have error container with id taxon-id-errors")))))

(deftest test-create-accepts-every-field-the-form-posts
  (tf/testing "the schema is a closed map, so a field missing from it is
               dropped in silence. Provenance, ID qualifier and supplier were
               all discarded on create and could only be set by saving and then
               editing — and with no provenance the Collection tab stays
               permanently unavailable."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (-> sess (peri/request "/accession/new/") :response :body)
            posted (->> (.select (Jsoup/parse ^String body) "#accession-form [name]")
                        (map #(.attr % "name"))
                        (remove #{"__anti-forgery-token"})
                        set)
            ;; `rest` also yields the schema's properties map, which is not
            ;; an entry.
            accepted (->> (rest create/FormParams)
                          (filter vector?)
                          (map (comp name first))
                          set)]
        (is (seq posted) "the form renders named controls")
        (is (empty? (set/difference posted accepted))
            (str "the form posts fields the schema drops: "
                 (set/difference posted accepted)))))))
