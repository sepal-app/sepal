(ns sepal.app.routes.material.detail.observations-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.observation.interface :as observation.i]
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
