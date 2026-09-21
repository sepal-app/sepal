(ns sepal.app.routes.location.detail.observations-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.observation.interface :as observation.i]
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
   [::location.i/factory :key/location] {:db *db*}})

(defn- observations-url [location]
  (str "/location/" (:location/id location) "/observations/"))

(defn- observation-url [location observation]
  (str "/location/" (:location/id location) "/observations/"
       (:observation/id observation) "/"))

(defn- cleanup-activity! [user]
  ;; The route logs an activity whose created_by is a not-null FK to user —
  ;; left behind, it blocks the fixture teardown from deleting this test's
  ;; user.
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-get-shows-an-empty-state
  (tf/testing "GET /location/:id/observations/ with no observations yet"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (observations-url location))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body "[data-observations-empty]")))))))

(deftest test-get-shows-an-existing-observation
  (tf/testing "GET /location/:id/observations/ renders a pre-existing observation"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [observation (observation.i/create! *db* {:resource-type :location
                                                     :resource-id (:location/id location)
                                                     :type "phenology"
                                                     :value "flowering"
                                                     :observed-on "2026-03-14"
                                                     :observed-by "A volunteer"
                                                     :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (observations-url location))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))))
        (is (some? (.selectFirst body "form#observation-form")))
        (is (.contains (.text body) "Flowering"))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-get-shows-the-creating-user-when-observed-by-is-empty
  (tf/testing "GET /location/:id/observations/ falls back to the creating user"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [observation (observation.i/create! *db* {:resource-type :location
                                                     :resource-id (:location/id location)
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (observations-url location))
            body (Jsoup/parse ^String (:body response))
            row (.selectFirst body (str "[data-observation-id=" (:observation/id observation) "]"))]
        (is (some? (.selectFirst row "[data-observation-observer]")))
        (is (.contains (.text row) (:user/email user)))
        (observation.i/delete! *db* (:observation/id observation))))))

(deftest test-post-creates-a-pest-observation-and-it-appears-on-the-page
  (tf/testing "a pest observation recorded against a glasshouse location appears on that location's page"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [url (observations-url location)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response] :as sess} (peri/request sess url
                                                      :request-method :post
                                                      :params {:__anti-forgery-token token
                                                               :type "pest"
                                                               :value "moderate"
                                                               :observed_on "2026-03-14"
                                                               :observed_by ""
                                                               :next_check_on ""
                                                               :note "Aphids on the Cattleya bench"})]
        (is (= 200 (:status response)))
        (let [{:keys [response]} (peri/request sess url)
              body (Jsoup/parse ^String (:body response))
              text (.text body)]
          (is (.contains text "Moderate"))
          (is (.contains text "Aphids on the Cattleya bench")))
        (let [observations (observation.i/get-for-resource *db* :location (:location/id location))]
          (observation.i/delete! *db* (:observation/id (first observations)))
          (cleanup-activity! user))))))

(deftest test-post-a-general-observation-needs-no-value
  (tf/testing "POST with type=general and a note but no value"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [url (observations-url location)
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
        (let [observations (observation.i/get-for-resource *db* :location (:location/id location))]
          (is (= 1 (count observations)))
          (is (= "general" (:observation/type (first observations))))
          (is (nil? (:observation/value (first observations))))
          (observation.i/delete! *db* (:observation/id (first observations)))
          (cleanup-activity! user))))))

(deftest test-post-a-mismatched-type-and-value-is-a-form-error-not-a-500
  (tf/testing "POST with a value that does not belong to the type"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [url (observations-url location)
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
        (is (empty? (observation.i/get-for-resource *db* :location (:location/id location))))))))

(deftest test-post-with-a-future-observed-on-is-rejected
  (tf/testing "POST with an observed_on in the future"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [url (observations-url location)
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
        (is (empty? (observation.i/get-for-resource *db* :location (:location/id location))))))))

(deftest test-post-with-a-past-next-check-on-is-accepted
  (tf/testing "a next_check_on in the past is how you backfill"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [url (observations-url location)
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
        (let [observations (observation.i/get-for-resource *db* :location (:location/id location))]
          (is (= 1 (count observations)))
          (is (= "2020-01-01" (:observation/next-check-on (first observations))))
          (observation.i/delete! *db* (:observation/id (first observations)))
          (cleanup-activity! user))))))

(deftest test-editing-with-a-future-date-targets-that-items-own-error-id
  (tf/testing "an edit form's error swap targets its own field, not the create form's or another item's"
    (fixtures)
    (fn [{:keys [user location]}]
      (let [observation (observation.i/create! *db* {:resource-type :location
                                                     :resource-id (:location/id location)
                                                     :type "general"
                                                     :observed-on "2026-03-14"
                                                     :note "existing"
                                                     :created-by (:user/id user)})
            url (observation-url location observation)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (observations-url location))
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
