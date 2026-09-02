(ns sepal.app.routes.accession.detail.collection-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.collection.interface :as coll.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(def empty-form-params
  "Base form params with all fields as empty strings (simulating empty HTML form).
   Note: srid always has a value (defaults to WGS-84 = 4326)."
  {:collector ""
   :collectors-code ""
   :collected-date ""
   :habitat ""
   :taxa ""
   :remarks ""
   :country ""
   :province ""
   :locality ""
   :lat ""
   :lng ""
   :srid "4326"
   :geo-uncertainty ""
   :elevation ""
   :elevation-accuracy ""})

(deftest test-collection-page-renders
  (tf/testing "GET collection page renders form"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             ;; The Collection tab is gated on provenance,
                                             ;; and the factory generates a random one.
                                             :data {:provenance-type :wild}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response]} (-> sess
                                   (peri/request collection-url))]
        (is (= 200 (:status response)))

        (let [body (Jsoup/parse ^String (:body response))
              form (.selectFirst body "form#collection-form")]
          (is (some? form)
              "Should have collection form")

          (is (some? (.attr form "hx-post"))
              "Form should have hx-post attribute")

          (is (= "none" (.attr form "hx-swap"))
              "Form should have hx-swap='none' for OOB error updates")

          ;; Check for key form fields
          (is (some? (.selectFirst body "input[name=\"collector\"]"))
              "Should have collector field")
          (is (some? (.selectFirst body "input[name=\"collected-date\"]"))
              "Should have collected-date field")
          (is (some? (.selectFirst body "input[name=\"country\"]"))
              "Should have country field")
          (is (some? (.selectFirst body "input[name=\"lat\"]"))
              "Should have latitude field")
          (is (some? (.selectFirst body "input[name=\"lng\"]"))
              "Should have longitude field"))))))

(deftest test-collection-page-shows-existing-data
  (tf/testing "GET collection page shows existing collection data"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             ;; The Collection tab is gated on provenance,
                                             ;; and the factory generates a random one.
                                             :data {:provenance-type :wild}}
     [::coll.i/factory :key/coll] {:db *db*
                                   :accession (ig/ref :key/accession)
                                   :collector "John Doe"
                                   :country "United States"}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response]} (-> sess
                                   (peri/request collection-url))]
        (is (= 200 (:status response)))

        (let [body (Jsoup/parse ^String (:body response))
              collector-input (.selectFirst body "input[name=\"collector\"]")
              country-input (.selectFirst body "input[name=\"country\"]")]
          (is (= "John Doe" (.attr collector-input "value"))
              "Should show existing collector value")
          (is (= "United States" (.attr country-input "value"))
              "Should show existing country value"))))))

(deftest test-create-collection
  (tf/testing "POST creates new collection"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             ;; The Collection tab is gated on provenance,
                                             ;; and the factory generates a random one.
                                             :data {:provenance-type :wild}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request collection-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request collection-url
                                                 :request-method :post
                                                 :params (merge empty-form-params
                                                                {:__anti-forgery-token token
                                                                 :collector "Jane Smith"
                                                                 :collected-date "2024-06-15"
                                                                 :country "Canada"
                                                                 :province "British Columbia"
                                                                 :locality "Vancouver"
                                                                 :habitat "Temperate rainforest"})))]
        ;; Should redirect on success
        (is (= 200 (:status response)))
        (is (= collection-url (get-in response [:headers "HX-Redirect"]))
            "Should redirect back to collection page")

        ;; Verify collection was created
        (let [coll (coll.i/get-by-accession-id *db* (:accession/id accession))]
          (is (= "Jane Smith" (:collection/collector coll)))
          (is (= "2024-06-15" (:collection/collected-date coll)))
          (is (= "Canada" (:collection/country coll)))
          (is (= "British Columbia" (:collection/province coll)))
          (is (= "Vancouver" (:collection/locality coll)))
          (is (= "Temperate rainforest" (:collection/habitat coll))))))))

(deftest test-update-collection
  (tf/testing "POST updates existing collection"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             ;; The Collection tab is gated on provenance,
                                             ;; and the factory generates a random one.
                                             :data {:provenance-type :wild}}
     [::coll.i/factory :key/coll] {:db *db*
                                   :accession (ig/ref :key/accession)
                                   :collector "Original Collector"
                                   :country "Mexico"}}
    (fn [{:keys [user accession coll]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request collection-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request collection-url
                                                 :request-method :post
                                                 :params (merge empty-form-params
                                                                {:__anti-forgery-token token
                                                                 :collector "Updated Collector"
                                                                 :country "Brazil"})))]
        ;; Should redirect on success
        (is (= 200 (:status response)))

        ;; Verify collection was updated (same ID)
        (let [updated-coll (coll.i/get-by-id *db* (:collection/id coll))]
          (is (= (:collection/id coll) (:collection/id updated-coll))
              "Should update existing collection, not create new one")
          (is (= "Updated Collector" (:collection/collector updated-coll)))
          (is (= "Brazil" (:collection/country updated-coll))))))))

(deftest test-create-collection-with-geo-coordinates
  (tf/testing "POST creates collection with geo coordinates"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             ;; The Collection tab is gated on provenance,
                                             ;; and the factory generates a random one.
                                             :data {:provenance-type :wild}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request collection-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request collection-url
                                                 :request-method :post
                                                 :params (merge empty-form-params
                                                                {:__anti-forgery-token token
                                                                 :collector "Geo Collector"
                                                                 :lat "45.5231"
                                                                 :lng "-122.6765"
                                                                 :geo-uncertainty "100"
                                                                 :elevation "50"})))]
        (is (= 200 (:status response)))

        ;; Verify geo coordinates were saved
        (let [coll (coll.i/get-by-accession-id *db* (:accession/id accession))
              geo (:collection/geo-coordinates coll)]
          (is (= 45.5231 (:lat geo)))
          (is (= -122.6765 (:lng geo)))
          (is (= 4326 (:srid geo)) "Should default to WGS-84 srid")
          (is (= 100 (:collection/geo-uncertainty coll)))
          (is (= 50 (:collection/elevation coll))))))))

(deftest test-collection-tabs-active
  (tf/testing "Collection tab is active on collection page"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             ;; The Collection tab is gated on provenance,
                                             ;; and the factory generates a random one.
                                             :data {:provenance-type :wild}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response]} (-> sess
                                   (peri/request collection-url))]
        (is (= 200 (:status response)))

        (let [body (Jsoup/parse ^String (:body response))
              nav (.selectFirst body "nav[aria-label='Accession sections']")
              current (.selectFirst body "nav[aria-label='Accession sections'] [aria-current=page]")]
          (is (some? nav)
              "sections are a nav, not a tablist — these links leave the page")
          (is (some? current) "the current section is marked")
          (is (= "Collection" (.text current))))))))

(deftest test-collection-route-is-guarded-when-not-wild
  (tf/testing "a disabled tab is not a security control — the route must refuse"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             :data {:provenance-type :not_wild}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/accession/"
                                                      (:accession/id accession)
                                                      "/collection/")))]
        (is (= 404 (:status response)))))))

(deftest test-collection-stays-reachable-when-data-already-exists
  (tf/testing "changing provenance away from wild must not strand thirteen
               fields behind a tab nobody can open"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             :data {:provenance-type :not_wild}}}
    (fn [{:keys [user accession]}]
      (coll.i/create! *db* {:accession-id (:accession/id accession)
                            :collector "A. Curator"})
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/accession/"
                                                      (:accession/id accession)
                                                      "/collection/")))]
        (is (= 200 (:status response))
            "the tab is available because the record already carries data")))))

(deftest test-collection-fields-save-through-the-form
  (tf/testing "POST sets collector's number and elevation accuracy"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             :data {:provenance-type :wild}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request collection-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request collection-url
                                                 :request-method :post
                                                 :params (merge empty-form-params
                                                                {:__anti-forgery-token token
                                                                 :collectors-code "BH9078"
                                                                 :elevation-accuracy "25"})))]
        (is (= 200 (:status response)))
        (is (= collection-url (get-in response [:headers "HX-Redirect"])))
        (let [coll (coll.i/get-by-accession-id *db* (:accession/id accession))]
          (is (= "BH9078" (:collection/collectors-code coll)))
          (is (= 25 (:collection/elevation-accuracy coll))))))))

(deftest test-collection-form-rejects-bad-elevation-accuracy
  (tf/testing "POST with elevation accuracy 0 returns 422 with a field error"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)
                                             :data {:provenance-type :wild}}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response] :as sess} (-> sess
                                            (peri/request collection-url))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request collection-url
                                                 :request-method :post
                                                 :params (merge empty-form-params
                                                                {:__anti-forgery-token token
                                                                 :elevation-accuracy "0"})))]
        (is (= 422 (:status response)))
        (let [body (Jsoup/parse ^String (:body response))]
          (is (some? (.selectFirst body "#elevation-accuracy-errors"))
              "elevation accuracy should have a field error"))
        (is (nil? (coll.i/get-by-accession-id *db* (:accession/id accession)))
            "nothing should have been saved")))))
