(ns sepal.app.routes.taxon.detail.synonyms-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.activity.interface :as activity.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.synonym.interface :as synonym.i]
            [sepal.synonym.interface.activity :as synonym.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- create-user! [db role password]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email :password password :role role})
    email))

(defn- ctx []
  {:schema-version (db.i/latest-version)})

(deftest test-adding-a-synonym-through-the-route
  (tf/testing "POST then GET shows the name, and records an activity event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            id (:taxon/id taxon)
            url (format "/taxon/%s/synonyms/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :synonym-name "Encyclia cochleata"})]
        (is (contains? #{200 303} (:status response)))
        (is (= ["Encyclia cochleata"]
               (mapv :synonym/synonym-name
                     (synonym.i/list-for-taxon (ctx) *db* id))))
        (testing "and the tab lists it"
          (is (re-find #"Encyclia cochleata"
                       (-> sess (peri/request url) :response :body))))
        (testing "and an activity event was written"
          (is (some #(= synonym.activity/created (:activity/type %))
                    (activity.i/get-by-resource *db* :resource-type :taxon :resource-id id))))
        ;; Clean up the activity row before the fixture map halts the user
        ;; factory below: activity.created_by references user(id), so deleting
        ;; the user first would fail the same way the earlier taxon-teardown
        ;; leak did.
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})
        (doseq [row (synonym.i/list-for-taxon (ctx) *db* id)]
          (synonym.i/remove-synonym! *db* (:synonym/id row)))))))

(deftest test-the-tab-reads-through-the-request-context
  ;; This is the end-to-end proof that :schema-version reaches a handler.
  ;; list-for-taxon gates on it: if the value did not arrive, the gate reads
  ;; "below the migration", returns [], and the name below would be absent from
  ;; the rendered page even though the row exists in the database.
  (tf/testing "a row written directly still renders"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [password "testpassword123"
            email (create-user! *db* :admin password)
            sess (app.test/login email password)
            id (:taxon/id taxon)
            row (synonym.i/add-synonym! *db* {:taxon-id id
                                              :synonym-name "Bucida buceras"})]
        (is (re-find #"Bucida buceras"
                     (-> sess
                         (peri/request (format "/taxon/%s/synonyms/" id))
                         :response :body)))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-an-empty-name-is-rejected
  (tf/testing "POST with a blank name"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [password "testpassword123"
            email (create-user! *db* :admin password)
            sess (app.test/login email password)
            id (:taxon/id taxon)
            url (format "/taxon/%s/synonyms/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :synonym-name ""})]
        (is (= 422 (:status response)))
        (is (empty? (synonym.i/list-for-taxon (ctx) *db* id)))))))

(deftest test-removing-a-synonym
  (tf/testing "DELETE removes the row and records an activity event"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            id (:taxon/id taxon)
            row (synonym.i/add-synonym! *db* {:taxon-id id
                                              :synonym-name "Dypsis lutescens"})
            {:keys [response] :as sess} (peri/request sess (format "/taxon/%s/synonyms/" id))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request
                                 sess
                                 (format "/taxon/%s/synonyms/%s/" id (:synonym/id row))
                                 :request-method :delete
                                 :headers {"x-csrf-token" token})]
        (is (contains? #{200 303} (:status response)))
        (is (empty? (synonym.i/list-for-taxon (ctx) *db* id)))
        (is (some #(= synonym.activity/deleted (:activity/type %))
                  (activity.i/get-by-resource *db* :resource-type :taxon :resource-id id)))
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))

(deftest test-a-get-on-the-row-route-does-not-delete
  ;; The row route is wired to :delete only. Reitit must refuse a GET here
  ;; rather than falling through to the same handler, which would let an
  ;; anti-forgery-exempt GET (an <img src>, a prefetch) delete a synonym.
  (tf/testing "GET is refused, and the row survives"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [password "testpassword123"
            email (create-user! *db* :admin password)
            sess (app.test/login email password)
            id (:taxon/id taxon)
            row (synonym.i/add-synonym! *db* {:taxon-id id
                                              :synonym-name "Aloe barbadensis"})
            {:keys [response]} (peri/request sess (format "/taxon/%s/synonyms/%s/" id (:synonym/id row)))]
        (is (not= 200 (:status response)))
        (is (= [(:synonym/id row)]
               (mapv :synonym/id (synonym.i/list-for-taxon (ctx) *db* id))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-a-database-below-the-gate-renders-an-empty-tab
  ;; The floor CI leg doesn't actually exercise this: the test system migrates
  ;; to latest before start! regardless of the schema-version option, so
  ;; taxon_synonym is always present there. This is the real coverage for "a
  ;; database below the migration gets an empty tab, not a 500".
  (tf/testing "the gate degrades instead of 500ing"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [password "testpassword123"
            email (create-user! *db* :admin password)
            sess (app.test/login email password)
            id (:taxon/id taxon)]
        (with-redefs [db.i/at-least-version? (constantly false)]
          (let [{:keys [response]} (peri/request sess (format "/taxon/%s/synonyms/" id))]
            (is (= 200 (:status response)))
            (is (re-find #"No synonyms yet" (:body response)))))))))
