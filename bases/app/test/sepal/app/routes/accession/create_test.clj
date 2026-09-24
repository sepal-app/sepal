(ns sepal.app.routes.accession.create-test
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.routes.accession.create :as create]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
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
        (is (some? (.selectFirst body "sepal-combobox#intended-location-id"))
            "the accession form should have an intended location picker")))))

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

(deftest test-supplier-field-offers-a-way-to-create-a-contact
  (tf/testing "the select only searches contacts that already exist, so a
               garden with none had no route to one from this form"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/accession/new/"))
            body (Jsoup/parse ^String (:body response))
            help (.selectFirst body "#supplier-contact-id-description")
            link (some-> help (.selectFirst "a"))
            select (.selectFirst body "#supplier-contact-id-input")]
        (is (some? link) "the help text carries a link to the contact form")
        (is (= "Create a contact" (.text link)))
        (is (str/ends-with? (.attr link "href") "/contact/new/"))
        (is (= "_blank" (.attr link "target"))
            "a new tab, because this form is usually half filled in by then")
        (is (str/includes? (.attr select "aria-describedby")
                           "supplier-contact-id-description")
            "the help is announced with the field rather than orphaned")))))

(deftest test-provenance-suggestion-endpoint
  (tf/testing "cultivar, Group and grex are the three ranks the cultivated
               plant code governs, so a plant of any of them is cultivated.
               Every other rank answers blank, which leaves the field alone
               rather than clearing it."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/cultivar] {:db *db* :rank :cultivar}
     [::taxon.i/factory :key/species] {:db *db* :rank :species}}
    (fn [{:keys [user cultivar species]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            suggest (fn [id]
                      (-> sess
                          (peri/request "/accession/provenance-suggestion/"
                                        :params {:taxon-id (str id)})
                          :response :body))]
        (is (= "cultivated" (suggest (:taxon/id cultivar))))
        (is (= "" (suggest (:taxon/id species))))
        (is (= "" (suggest 0)) "an unknown taxon suggests nothing")))))

(deftest test-only-the-create-form-suggests-a-provenance
  (tf/testing "the edit form must not reclassify an accession you are only
               correcting"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            ;; The hx-* attributes sit on a listener element beside the
            ;; select, not on the select: htmx marks its requesting element
            ;; with htmx-request as a request runs, and a picker that reacted
            ;; to its own class list changing closed its open dropdown.
            listener (fn [path]
                       (-> sess
                           (peri/request path)
                           :response :body
                           (as-> b (Jsoup/parse ^String b))
                           (.selectFirst "#provenance-suggestion")))]
        (is (= "/accession/provenance-suggestion/"
               (.attr (listener "/accession/new/") "hx-get"))
            "the create form asks")
        (is (nil? (listener (str "/accession/" (:accession/id accession) "/general/")))
            "the edit form does not")))))

(deftest test-taxon-id-prefills-the-form
  (tf/testing "a taxon's \"Add an accession\" names the taxon. The select is
               searched client-side, so without the option rendered here the
               field arrives empty and the link saves nothing."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/accession/new/"
                                                 :params {:taxon-id (str (:taxon/id taxon))}))
            body (Jsoup/parse ^String (:body response))
            picker (.selectFirst body "sepal-combobox#taxon-id")]
        (is (some? picker))
        (is (= (str (:taxon/id taxon)) (.attr picker "data-value")))
        (is (= (:taxon/name taxon) (.attr picker "data-text"))
            "the name is on the element, so the field reads it back without a
             request — it is what the input shows on arrival")))))

(deftest test-an-unknown-taxon-id-is-ignored
  (tf/testing "a stale link should still render a usable empty form"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request "/accession/new/"
                                                 :params {:taxon-id "0"}))
            body (Jsoup/parse ^String (:body response))
            picker (.selectFirst body "sepal-combobox#taxon-id")]
        (is (= 200 (:status response)))
        (is (some? picker))
        (is (str/blank? (.attr picker "data-value")) "no taxon chosen")))))

(deftest test-a-picker-starts-empty-and-knows-where-to-search
  ;; This replaces a test for an empty placeholder <option>, which existed
  ;; because the old widget would otherwise select the first search result and
  ;; then hide it. The picker has no options until you type, so there is nothing to
  ;; select by accident — what matters now is that it starts with no value and
  ;; carries the endpoint it searches.
  (tf/testing "each picker on the create form"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess (peri/request "/accession/new/"))
            body (Jsoup/parse ^String (:body response))]
        (doseq [id ["taxon-id" "supplier-contact-id" "intended-location-id"]]
          (let [picker (.selectFirst body (str "sepal-combobox#" id))]
            (is (some? picker) (str id " is not a picker"))
            (is (str/blank? (.attr picker "data-value"))
                (str id " starts with a value"))
            (is (not (str/blank? (.attr picker "data-url")))
                (str id " has nowhere to search"))
            (is (= id (.attr picker "name"))
                (str id " would not submit under its own name"))))))))

(deftest test-every-field-survives-a-create
  (tf/testing "one assertion per field, because these were found one at a time
               by hand: provenance, supplier and both dates each went missing
               separately. A field dropped anywhere between the form, the
               closed FormParams map and the closed CreateAccession spec
               disappears without an error."
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::contact.i/factory :key/contact] {:db *db*}
     [::location.i/factory :key/location] {:db *db*}}
    (fn [{:keys [user taxon contact location]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess (peri/request "/accession/new/"))
              token (test.i/response-anti-forgery-token response)
              params {:__anti-forgery-token token
                      :code "ALLFIELDS-1"
                      :taxon-id (str (:taxon/id taxon))
                      :id-qualifier "aff"
                      :id-qualifier-rank "genus"
                      :provenance-type "wild"
                      :wild-provenance-status "wild_native"
                      :supplier-contact-id (str (:contact/id contact))
                      :intended-location-id (str (:location/id location))
                      :date-received "2026-03-04"
                      :date-accessioned "2026-03-05"
                      :received-type "seed"
                      :quantity-received "7"}
              {:keys [response]} (-> sess
                                     (peri/request "/accession/new/"
                                                   :request-method :post
                                                   :params params))]
          (is (= 200 (:status response))
              (str "expected 200, got " (:status response) ": " (:body response)))
          (let [redirect (get-in response [:headers "HX-Redirect"])
                id (parse-long (last (remove empty? (str/split redirect #"/"))))
                saved (accession.i/get-by-id *db* id)]
            (is (= "ALLFIELDS-1" (:accession/code saved)))
            (is (= (:taxon/id taxon) (:accession/taxon-id saved)))
            (is (= :aff (:accession/id-qualifier saved)) "id qualifier")
            (is (= :genus (:accession/id-qualifier-rank saved)) "id qualifier rank")
            (is (= :wild (:accession/provenance-type saved)) "provenance type")
            (is (= :wild_native (:accession/wild-provenance-status saved))
                "wild provenance status")
            (is (= (:contact/id contact) (:accession/supplier-contact-id saved))
                "supplier")
            (is (= (:location/id location) (:accession/intended-location-id saved))
                "intended location")
            (is (= "2026-03-04" (:accession/date-received saved)) "date received")
            (is (= "2026-03-05" (:accession/date-accessioned saved))
                "date accessioned")
            (is (= :seed (:accession/received-type saved)) "received type")
            (is (= 7 (:accession/quantity-received saved)) "quantity received")

            ;; Saving worked all along. What made every one of these look
            ;; broken is the edit page rendering them blank — and a blank
            ;; control posts an empty string, so the next save erases the
            ;; value for real.
            (let [body (-> sess
                           (peri/request (str "/accession/" id "/general/"))
                           :response :body
                           (as-> b (Jsoup/parse ^String b)))
                  input-value (fn [id] (some-> (.selectFirst body (str "input#" id))
                                               (.val)))
                  selected (fn [id] (some-> (.selectFirst body
                                                          (str "select#" id " option[selected]"))
                                            (.attr "value")))
                  ;; A picker carries its value on the element rather than on a
                  ;; selected <option>, since its options are not in the page.
                  picked (fn [id] (some-> (.selectFirst body
                                                        (str "sepal-combobox#" id))
                                          (.attr "data-value")))]
              (is (= "ALLFIELDS-1" (input-value "code")) "code renders")
              (is (= "2026-03-04" (input-value "date-received"))
                  "date received renders")
              (is (= "2026-03-05" (input-value "date-accessioned"))
                  "date accessioned renders")
              (is (= "7" (input-value "quantity-received"))
                  "quantity received renders")
              (is (= (str (:taxon/id taxon)) (picked "taxon-id"))
                  "taxon renders as selected")
              (is (= (str (:contact/id contact)) (picked "supplier-contact-id"))
                  "supplier renders as selected")
              (is (= (str (:location/id location)) (picked "intended-location-id"))
                  "intended location renders as selected")
              (is (= "aff" (selected "id-qualifier")) "id qualifier renders")
              (is (= "genus" (selected "id-qualifier-rank"))
                  "id qualifier rank renders")
              (is (= "wild" (selected "provenance-type"))
                  "provenance type renders")
              (is (= "wild_native" (selected "wild-provenance-status"))
                  "wild provenance status renders")
              (is (= "seed" (selected "received-type"))
                  "received type renders"))))
        (finally
          (jdbc.sql/delete! *db* :accession {:code "ALLFIELDS-1"})
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-create-accession-rejects-a-future-date
  (tf/testing "a future received date is a field error and nothing is created"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (try
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (-> sess (peri/request "/accession/new/"))
              body (Jsoup/parse ^String (:body response))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (-> sess
                                     (peri/request "/accession/new/"
                                                   :request-method :post
                                                   :params (receipt-params
                                                             taxon
                                                             :__anti-forgery-token token
                                                             :date-received "2999-01-01")))]
          (is (some? (.selectFirst body "input[name=date-received][max]"))
              "the picker is capped at today")
          (is (= 422 (:status response)))
          (is (some? (.selectFirst (Jsoup/parse ^String (:body response)) "#date-received-errors")))
          (is (empty? (jdbc.sql/find-by-keys *db* :accession {:code "RCPT-1"}))))
        (finally
          (jdbc.sql/delete! *db* :accession {:code "RCPT-1"})
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))
