(ns sepal.app.routes.taxon.panel-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.synonym.interface :as synonym.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- panel-body
  "The panel fragment as the browser gets it.

  Requested through the real route rather than by calling `panel-content`
  directly: the panel renders `z/url-for` links, which need the reitit router
  bound on the request, so it cannot be rendered outside one."
  [sess taxon-id]
  (-> sess
      (peri/request (format "/taxon/%s/panel/" taxon-id))
      :response :body))

(deftest test-a-taxons-synonyms-appear-in-the-panel
  (tf/testing "a garden synonym reaches the rendered panel"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db*}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Encyclia cochleata"})
            body (panel-body sess (:taxon/id taxon))]
        (is (re-find #"Synonyms" body))
        (is (re-find #"Encyclia cochleata" body))
        (testing "and a garden row carries no WFO badge -- nobody needs telling
                  that their own assertion is theirs"
          (is (not (re-find #"spl-badge--neutral" body))))
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-a-taxons-distribution-appears-in-the-panel
  (tf/testing "distribution reaches the rendered panel"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/taxon] {:db *db* :distribution "Central America"}}
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            body (panel-body sess (:taxon/id taxon))]
        (is (re-find #"Distribution" body))
        (is (re-find #"Central America" body))))))

(deftest test-another-taxons-synonym-does-not-leak-in
  ;; The section is present but empty for a taxon with none, matching External
  ;; Links and Activity. Asserting a *name* is absent rather than the section is
  ;; what lets this fail: a row exists in the table, so a query that ignored
  ;; taxon_id would leak it here.
  (tf/testing "no synonyms of its own"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}
     [::taxon.i/factory :key/a] {:db *db*}
     [::taxon.i/factory :key/b] {:db *db*}}
    (fn [{:keys [user a b]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id b)
                                              :synonym-name "Encyclia cochleata"})
            body (panel-body sess (:taxon/id a))]
        (is (re-find #"Synonyms" body) "the section is present, not absent")
        (is (not (re-find #"Encyclia cochleata" body))
            "another taxon's synonym must not appear in this panel")
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))
