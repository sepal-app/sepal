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

(deftest test-a-database-below-the-gate-offers-no-add-form
  ;; The read is gated, so on a floor database the tab was showing "No synonyms
  ;; yet" next to a working-looking Add control that cannot store anything: the
  ;; POST reached add-synonym!, SQLite refused the missing table, and the form
  ;; came back as an empty 422 with the typed name gone.
  (tf/testing "no form, and the POST refused rather than 422ing"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [password "testpassword123"
            email (create-user! *db* :admin password)
            sess (app.test/login email password)
            id (:taxon/id taxon)
            url (format "/taxon/%s/synonyms/" id)
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)]
        (is (re-find #"name=\"synonym-name\"" (:body response))
            "the form is offered when the table is there, so its absence below
             is the gate and not a rename")
        (with-redefs [db.i/at-least-version? (constantly false)]
          (let [{:keys [response]} (peri/request sess url)]
            (is (= 200 (:status response)))
            (is (not (re-find #"name=\"synonym-name\"" (:body response)))))
          (let [{:keys [response]} (peri/request sess url
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :synonym-name "Ficus elastica"})]
            (is (= 404 (:status response))
                "a direct POST must be refused, not left to fail in SQLite")))
        (is (empty? (synonym.i/list-for-taxon (ctx) *db* id)))))))

(deftest test-a-non-numeric-synonym-id-deletes-nothing
  ;; WFO rows carry no :synonym/id, and parse-long of a non-numeric segment is
  ;; nil, so an unguarded `(= synonym-id (:synonym/id %))` matches the first WFO
  ;; row on `(= nil nil)`. The list is stubbed to carry one, because the test
  ;; process has no WFO reference file and a list of local rows alone cannot
  ;; tell the guarded path from the unguarded one.
  (tf/testing "DELETE .../synonyms/abc/"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [password "testpassword123"
            email (create-user! *db* :admin password)
            sess (app.test/login email password)
            id (:taxon/id taxon)
            row (synonym.i/add-synonym! *db* {:taxon-id id
                                              :synonym-name "Cattleya labiata"})
            {:keys [response] :as sess} (peri/request sess (format "/taxon/%s/synonyms/" id))
            token (test.i/response-anti-forgery-token response)
            removed (atom [])
            real-list synonym.i/list-for-taxon]
        (with-redefs [synonym.i/list-for-taxon
                      (fn [ctx db taxon-id]
                        (conj (vec (real-list ctx db taxon-id))
                              {:synonym/synonym-name "Encyclia cochleata"
                               :synonym/source "wfo"}))
                      synonym.i/remove-synonym!
                      (fn [_db synonym-id] (swap! removed conj synonym-id) nil)]
          (let [{:keys [response]} (peri/request
                                     sess
                                     (format "/taxon/%s/synonyms/abc/" id)
                                     :request-method :delete
                                     :headers {"x-csrf-token" token})]
            (is (not= 500 (:status response)))
            (is (empty? @removed)
                "a non-numeric id selected a row and tried to delete it")))
        (is (= [(:synonym/id row)]
               (mapv :synonym/id (synonym.i/list-for-taxon (ctx) *db* id))))
        (is (not-any? #(= synonym.activity/deleted (:activity/type %))
                      (activity.i/get-by-resource *db* :resource-type :taxon :resource-id id))
            "an activity event naming a row that never existed")
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-a-database-below-the-gate-renders-an-empty-tab
  ;; The floor CI leg doesn't actually exercise this: the test system migrates
  ;; to latest before start! regardless of the schema-version option, so
  ;; taxon_synonym is always present there. This is the real coverage for "a
  ;; database below the migration gets an empty tab, not a 500".
  ;;
  ;; The taxon must have a real row before the gate is forced off. An empty
  ;; taxon with no rows would render the same empty state whether the gate is
  ;; checked or skipped entirely, which proves nothing about the branch
  ;; existing at all -- an ungated query against an empty result set looks
  ;; identical to a gated one. Writing a row first and asserting its name is
  ;; *absent* once the gate reports "not available" is the only assertion
  ;; that can tell the two paths apart.
  (tf/testing "the gate degrades instead of 500ing"
    {[::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [taxon]}]
      (let [password "testpassword123"
            email (create-user! *db* :admin password)
            sess (app.test/login email password)
            id (:taxon/id taxon)
            row (synonym.i/add-synonym! *db* {:taxon-id id
                                              :synonym-name "Ficus elastica"})]
        (with-redefs [db.i/at-least-version? (constantly false)]
          (let [{:keys [response]} (peri/request sess (format "/taxon/%s/synonyms/" id))]
            (is (= 200 (:status response)))
            (is (not (re-find #"Ficus elastica" (:body response)))
                "the gate must filter out a row that really exists once it reports the table unavailable")
            (is (re-find #"No synonyms yet" (:body response)))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))
