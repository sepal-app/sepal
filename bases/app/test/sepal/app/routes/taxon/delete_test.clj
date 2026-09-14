(ns sepal.app.routes.taxon.delete-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.synonym.interface :as synonym.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

;; synonym.i/list-for-taxon takes a context carrying :synonym-reference, the
;; WFO reference pool. A nil pool yields the garden's own rows, which is all
;; this namespace writes.
(def ^:private ctx {:synonym-reference nil})

(defn- fixtures
  ([] (fixtures :editor))
  ([role]
   {[::user.i/factory :key/user] {:db *db*
                                  :password "testpassword123"
                                  :role role}
    [::taxon.i/factory :key/taxon] {:db *db*}}))

(defn- delete-url [taxon]
  (str "/taxon/" (:taxon/id taxon) "/delete/"))

(defn- clear-activity! [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-a-wfo-taxon-cannot-be-deleted-through-the-route
  (tf/testing "GET /taxon/:id/delete/ for a WFO name"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}}
    (fn [{:keys [user]}]
      (let [taxon (taxon.i/create! *db* {:name "Wfo route test"
                                         :rank :species
                                         :wfo-taxon-id "wfo-0000000002-2025-06"})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url taxon))
            body (Jsoup/parse ^String (:body response))]
        (is (nil? (.selectFirst body "form[method=post]")))
        (is (re-find #"(?i)world flora online" (.text body)))
        (taxon.i/delete! *db* (:taxon/id taxon))))))

(deftest test-deleting-a-taxon-takes-its-synonyms
  (tf/testing "POST /taxon/:id/delete/ with synonyms"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (let [id (:taxon/id taxon)]
        ;; The factory generates wfo-taxon-id at random, and one would block
        ;; the delete for a reason this test is not about.
        (taxon.i/update! *db* id {:wfo-taxon-id nil})
        (synonym.i/add-synonym! *db* {:taxon-id id
                                      :synonym-name "Encyclia cochleata"})
        (is (seq (synonym.i/list-for-taxon ctx *db* id)))
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (peri/request sess (delete-url taxon))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (peri/request sess (delete-url taxon)
                                               :request-method :post
                                               :params {:__anti-forgery-token token})]
          (try
            (is (= 303 (:status response)))
            (is (nil? (taxon.i/get-by-id *db* id)))
            (is (empty? (synonym.i/list-for-taxon ctx *db* id))
                "a synonym is the taxon's other name and has no meaning without it")
            (finally
              (clear-activity! user))))))))

(deftest test-a-reader-cannot-delete
  (tf/testing "as a reader"
    (fixtures :reader)
    (fn [{:keys [user taxon]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url taxon))]
        (is (= 302 (:status response)))
        (is (some? (taxon.i/get-by-id *db* (:taxon/id taxon))))))))
