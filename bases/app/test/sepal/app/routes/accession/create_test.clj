(ns sepal.app.routes.accession.create-test
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.routes.accession.create :as create]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.taxon.interface :as taxon.i]
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

;; Every key in the closed FormParams map is required, so a POST that omits
;; one is a 422 -- which is why this posts the whole form and not three fields.
;; The comment above `create/FormParams` records what a *missing* key silently
;; did before.
(defn- create-params [taxon location]
  {:code "ACC-IL-1"
   :taxon-id (str (:taxon/id taxon))
   :id-qualifier ""
   :id-qualifier-rank ""
   :provenance-type ""
   :wild-provenance-status ""
   :supplier-contact-id ""
   :date-received ""
   :date-accessioned ""
   :received-type ""
   :quantity-received ""
   :intended-location-id (str (:location/id location))})

(deftest test-create-accession-with-intended-location
  (tf/testing "POST with an intended location saves it"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user taxon location]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess
                                              (peri/request "/accession/new/"))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request "/accession/new/"
                                                   :request-method :post
                                                   :params (assoc (create-params taxon location)
                                                                  :__anti-forgery-token token)))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (let [redirect (get-in response [:headers "HX-Redirect"])
                id (parse-long (last (remove empty? (str/split redirect #"/"))))
                accession (accession.i/get-by-id *db* id)]
            (is (= (:location/id location)
                   (:accession/intended-location-id accession)))))
        (finally
          ;; The accession is created by HTTP, so no factory tears it down --
          ;; and while it exists the location factory cannot delete its
          ;; location. Creating also writes an activity row referencing the
          ;; factory user, whose teardown hard-deletes it.
          (jdbc.sql/delete! *db* :accession {:code "ACC-IL-1"})
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-create-accession-form-has-an-intended-location-picker
  (tf/testing "the form renders the picker"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/accession/new/"))
            body (Jsoup/parse ^String (:body response))]
        (is (some? (.selectFirst body "select#intended-location-id"))
            "the accession form should have an intended location select")))))

(defn- receipt-params
  "The full field set a browser submits, with empty strings for blank fields.
  `create/FormParams` is closed and every key is required, so a POST that omits
  one is a 422 rather than a save."
  [taxon & {:as overrides}]
  (merge {:code "RCPT-1"
          :taxon-id (str (:taxon/id taxon))
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
         overrides))

(deftest test-create-accession-with-receipt-fields
  (tf/testing "POST with a received type and quantity saves both"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess (peri/request "/accession/new/"))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request "/accession/new/"
                                                   :request-method :post
                                                   :params (receipt-params
                                                             taxon
                                                             :__anti-forgery-token token
                                                             :received-type "bare_root_plant"
                                                             :quantity-received "3")))]
          (is (= 200 (:status response))
              (str "Expected 200, got " (:status response) " with body: " (:body response)))
          (let [redirect (get-in response [:headers "HX-Redirect"])
                id (parse-long (re-find #"\d+" redirect))
                accession (accession.i/get-by-id *db* id)]
            (is (= :bare_root_plant (:accession/received-type accession)))
            (is (= 3 (:accession/quantity-received accession)))))
        (finally
          ;; The accession is created by HTTP, so no factory tears it down --
          ;; and while it exists the taxon factory cannot delete its taxon.
          ;; Creating also writes an activity row referencing the factory
          ;; user, whose teardown hard-deletes that user -- clean up so the
          ;; FK lets both proceed.
          (jdbc.sql/delete! *db* :accession {:code "RCPT-1"})
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-create-accession-rejects-invalid-received-type
  (tf/testing "POST with a received type outside the vocabulary returns 422"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess (peri/request "/accession/new/"))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/accession/new/"
                                                 :request-method :post
                                                 :params (receipt-params
                                                           taxon
                                                           :__anti-forgery-token token
                                                           :code "RCPT-2"
                                                           :received-type "not_a_propagule")))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response)))
        (let [body (Jsoup/parse ^String (:body response))]
          (is (some? (.selectFirst body "#received-type-errors"))
              "received-type should have a field error"))))))

(deftest test-create-accession-rejects-negative-quantity-received
  (tf/testing "POST with a negative quantity received returns 422"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (-> sess (peri/request "/accession/new/"))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/accession/new/"
                                                 :request-method :post
                                                 :params (receipt-params
                                                           taxon
                                                           :__anti-forgery-token token
                                                           :code "RCPT-3"
                                                           :quantity-received "-1")))]
        (is (= 422 (:status response))
            (str "Expected 422, got " (:status response)))
        (let [body (Jsoup/parse ^String (:body response))]
          (is (some? (.selectFirst body "#quantity-received-errors"))
              "quantity-received should have a field error"))))))

(deftest test-the-receipt-section-is-titled-receipt-and-renders-its-controls
  (tf/testing "the section holding the four receipt fields is named for the
               event, not for two of its four fields, and the two new
               controls are actually rendered by id"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            html (-> sess (peri/request "/accession/new/") :response :body)
            body (Jsoup/parse ^String html)
            titles (->> (.select body ".spl-form-section-title")
                        (map #(.text %))
                        set)]
        (is (contains? titles "Receipt"))
        (is (not (contains? titles "Dates")))

        (is (some? (.selectFirst body "select#received-type"))
            "the propagule picker renders; renaming it 422s a real browser submit
             while every params-map test still passes")
        (is (some? (.selectFirst body "input#quantity-received"))
            "same for the quantity input")))))
