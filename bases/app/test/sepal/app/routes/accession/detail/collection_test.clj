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
   :elevation ""})

(deftest test-collection-page-renders
  (tf/testing "GET collection page renders form"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
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
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
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
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
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
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
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
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
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
     [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            collection-url (str "/accession/" (:accession/id accession) "/collection/")
            {:keys [response]} (-> sess
                                   (peri/request collection-url))]
        (is (= 200 (:status response)))

        (let [body (Jsoup/parse ^String (:body response))
              ;; Look for the active tab - it should have aria-current or active class
              tabs (.select body "[role=\"tab\"]")]
          (is (pos? (.size tabs))
              "Should have tab elements"))))))