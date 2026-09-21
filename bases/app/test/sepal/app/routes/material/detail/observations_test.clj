(ns sepal.app.routes.material.detail.observations-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.activity :as observation.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

;; A function, not a top-level def: *db* is a dynamic var bound only while
;; default-system-fixture runs, and a def's value expression is evaluated once
;; at namespace load — before that binding exists — which would freeze :db at
;; nil for every test.
(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::contact.i/factory :key/contact] {:db *db*}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db*
                                           :taxon (ig/ref :key/taxon)
                                           :contact (ig/ref :key/contact)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)}})

(defn- observations-url [material]
  (str "/material/" (:material/id material) "/observations/"))

(defn- observation-url [material observation]
  (str "/material/" (:material/id material) "/observations/"
       (:observation/id observation) "/"))

(defn- cleanup-activity! [user]
  ;; The route logs an activity whose created_by is a not-null FK to user —
  ;; left behind, it blocks the fixture teardown from deleting this test's
  ;; user.
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-get-shows-an-empty-state
  (tf/testing "GET /material/:id/observations/ with no observations yet"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (observations-url material))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body "[data-observations-empty]")))))))

(deftest test-get-shows-an-existing-observation
  (tf/testing "GET /material/:id/observations/ renders a pre-existing observation"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id (:material/id material)
                                                     :type "phenology"
                                                     :value "flowering"
                                                     :observed-on "2026-03-14"
                                                     :observed-by "A volunteer"
                                                     :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (observations-url material))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))))
        (is (some? (.selectFirst body "form#observation-form")))
        (is (.contains (.text body) "Flowering"))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-get-shows-the-creating-user-when-observed-by-is-empty
  (tf/testing "GET /material/:id/observations/ falls back to the creating user"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id (:material/id material)
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (observations-url material))
            body (Jsoup/parse ^String (:body response))
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (some? (.selectFirst row "[data-observation-observer]")))
        (is (.contains (.text row) (:user/email user)))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-post-creates-an-observation-with-a-value
  (tf/testing "POST with a type and a value that belong together"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [url (observations-url material)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :type "phenology"
                                                      :value "flowering"
                                                      :observed_on "2026-03-14"
                                                      :observed_by "A volunteer"
                                                      :next_check_on ""
                                                      :note ""})]
        (is (= 200 (:status response)))
        (let [observations (observation.i/get-for-resource *db* :material (:material/id material))]
          (is (= 1 (count observations)))
          (is (= "phenology" (:observation/type (first observations))))
          (is (= "flowering" (:observation/value (first observations))))
          (is (= "A volunteer" (:observation/observed-by (first observations))))
          (let [body (Jsoup/parse ^String (:body response))]
            (is (some? (.selectFirst body "#observations-list")))
            (is (.contains (.text body) "Flowering")))
          (observation.i/delete! *db* (:observation/id (first observations)))
          (cleanup-activity! user))))))

(deftest test-post-a-general-observation-needs-no-value
  (tf/testing "POST with type=general and a note but no value"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [url (observations-url material)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :type "general"
                                                      :value ""
                                                      :observed_on "2026-03-14"
                                                      :observed_by ""
                                                      :next_check_on ""
                                                      :note "Found dead in the orchid nursery"})]
        (is (= 200 (:status response)))
        (let [observations (observation.i/get-for-resource *db* :material (:material/id material))]
          (is (= 1 (count observations)))
          (is (= "general" (:observation/type (first observations))))
          (is (nil? (:observation/value (first observations))))
          (observation.i/delete! *db* (:observation/id (first observations)))
          (cleanup-activity! user))))))

(deftest test-post-a-mismatched-type-and-value-is-a-form-error-not-a-500
  (tf/testing "POST with a value that does not belong to the type"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [url (observations-url material)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :type "phenology"
                                                      :value "severe"
                                                      :observed_on "2026-03-14"
                                                      :observed_by ""
                                                      :next_check_on ""
                                                      :note ""})]
        (is (= 422 (:status response)))
        (is (empty? (observation.i/get-for-resource *db* :material (:material/id material))))))))

(deftest test-post-with-a-future-observed-on-is-rejected
  (tf/testing "POST with an observed_on in the future"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [url (observations-url material)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :type "general"
                                                      :value ""
                                                      :observed_on "2099-01-01"
                                                      :observed_by ""
                                                      :next_check_on ""
                                                      :note "Not yet"})]
        (is (= 422 (:status response)))
        (is (empty? (observation.i/get-for-resource *db* :material (:material/id material))))))))

(deftest test-post-with-a-past-next-check-on-is-accepted
  (tf/testing "a next_check_on in the past is how you backfill"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [url (observations-url material)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :type "general"
                                                      :value ""
                                                      :observed_on "2026-03-14"
                                                      :observed_by ""
                                                      :next_check_on "2020-01-01"
                                                      :note "Backfilled"})]
        (is (= 200 (:status response)))
        (let [observations (observation.i/get-for-resource *db* :material (:material/id material))]
          (is (= 1 (count observations)))
          (is (= "2020-01-01" (:observation/next-check-on (first observations))))
          (observation.i/delete! *db* (:observation/id (first observations)))
          (cleanup-activity! user))))))

(deftest test-editing-with-a-future-date-targets-that-items-own-error-id
  (tf/testing "an edit form's error swap targets its own field, not the create form's or another item's"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id (:material/id material)
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :note "existing"
                                                     :created-by (:user/id user)})
            url (observation-url material observation)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (observations-url material))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :type "general"
                                                      :value ""
                                                      :observed_on "2099-01-01"
                                                      :observed_by ""
                                                      :next_check_on ""
                                                      :note "existing"})]
        (is (= 422 (:status response)))
        (is (.contains (:body response)
                       (str "id=\"observed_on-" (:observation/id observation) "-errors\"")))
        (is (not (.contains (:body response) "id=\"observed_on-errors\"")))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-post-to-an-observation-updates-it
  (tf/testing "POST /material/:id/observations/:observation-id/"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id (:material/id material)
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :note "before"
                                                     :created-by (:user/id user)})
            url (observation-url material observation)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (observations-url material))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :type "general"
                                                      :value ""
                                                      :observed_on "2026-03-14"
                                                      :observed_by ""
                                                      :next_check_on ""
                                                      :note "after"})]
        (is (= 200 (:status response)))
        (is (= "after" (:observation/note (observation.i/get-by-id *db* (:observation/id observation)))))
        (is (some #(= observation.activity/updated (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :material :resource-id (:material/id material))))
        (observation.i/delete! *db* (:observation/id observation))
        (cleanup-activity! user)))))

(deftest test-delete-removes-the-observation
  (tf/testing "DELETE /material/:id/observations/:observation-id/"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [observation (observation.i/create! *db* {:resource-type :material
                                                     :resource-id (:material/id material)
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :note "written by mistake"
                                                     :created-by (:user/id user)})
            url (observation-url material observation)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (observations-url material))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 200 (:status response)))
        (is (nil? (observation.i/get-by-id *db* (:observation/id observation))))
        (is (some #(= observation.activity/deleted (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :material :resource-id (:material/id material))))
        (cleanup-activity! user)))))

(deftest test-an-observation-belonging-to-another-resource-is-not-reachable
  (tf/testing "DELETE with an observation-id from a different subject"
    (fixtures)
    (fn [{:keys [user material location]}]
      ;; The observation is on the location; the URL says material. Without a
      ;; check that the observation belongs to the resource in the path, any
      ;; observation in the garden is deletable through any resource's URL.
      (let [observation (observation.i/create! *db* {:resource-type :location
                                                     :resource-id (:location/id location)
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :note "on the location"
                                                     :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (observations-url material))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (observation-url material observation)
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 404 (:status response)))
        (is (some? (observation.i/get-by-id *db* (:observation/id observation))))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-the-material-tab-offers-no-way-to-write-a-note
  (tf/testing "the tab list says Observations, not Notes"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (observations-url material))
            body (Jsoup/parse ^String (:body response))
            tab-text (.text (.selectFirst body ".spl-tabs"))]
        (is (.contains tab-text "Observations"))
        (is (not (.contains tab-text "Notes")))))))
