(ns sepal.app.routes.taxon.detail.notes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.note.interface :as note.i]
            [sepal.synonym.interface :as synonym.i]
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
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::taxon.i/factory :key/other-taxon] {:db *db*}})

(defn- notes-url [taxon]
  (str "/taxon/" (:taxon/id taxon) "/notes/"))

(deftest test-post-creates-a-note
  (tf/testing "POST /taxon/:id/notes/"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (let [url (notes-url taxon)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :body "BBG grows the white form only"})]
        (is (= 200 (:status response)))
        (let [notes (note.i/get-for-resource *db* :taxon (:taxon/id taxon))]
          (is (= ["BBG grows the white form only"] (mapv :note/body notes)))
          (is (= (:user/id user) (:note/created-by (first notes))))
          (let [body (Jsoup/parse ^String (:body response))]
            (is (some? (.selectFirst body "#notes-list")))
            (is (= 1 (.size (.select body "[data-note-id]")))))
          (note.i/delete! *db* (:note/id (first notes)))
          ;; The route also logs a note/created activity, whose created_by is
          ;; a not-null FK to user — left behind, it blocks the fixture
          ;; teardown from deleting this test's user.
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-post-with-an-empty-body-is-rejected
  (tf/testing "POST with a blank body"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (let [url (notes-url taxon)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :body ""})]
        (is (= 422 (:status response)))
        (is (empty? (note.i/get-for-resource *db* :taxon (:taxon/id taxon))))))))

(deftest test-get-lists-existing-notes
  (tf/testing "GET /taxon/:id/notes/"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (let [note (note.i/create! *db* {:body "imported from Bauble"
                                       :resource-type :taxon
                                       :resource-id (:taxon/id taxon)
                                       :created-by nil})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (notes-url taxon))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body (str "[data-note-id=" (:note/id note) "]"))))
        (testing "an authorless imported note renders without an author element"
          (is (nil? (.selectFirst body (str "[data-note-id=" (:note/id note) "] [data-note-author]")))))
        (note.i/delete! *db* (:note/id note))))))

(deftest test-the-panel-still-shows-synonyms-on-the-notes-tab
  (tf/testing "GET /taxon/:id/notes/ carries the panel's Synonyms section"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (let [row (synonym.i/add-synonym! *db* {:taxon-id (:taxon/id taxon)
                                              :synonym-name "Encyclia cochleata"})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (notes-url taxon))]
        (is (= 200 (:status response)))
        (is (re-find #"Encyclia cochleata" (:body response))
            "the Notes tab's panel must not blank out the taxon's synonyms")
        (synonym.i/remove-synonym! *db* (:synonym/id row))))))

(deftest test-post-to-a-note-updates-it
  (tf/testing "POST /taxon/:id/notes/:note-id/"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (let [note (note.i/create! *db* {:body "befre"
                                       :resource-type :taxon
                                       :resource-id (:taxon/id taxon)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url taxon))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url taxon) (:note/id note) "/")
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :body "before"})]
        (is (= 200 (:status response)))
        (is (= "before" (:note/body (note.i/get-by-id *db* (:note/id note)))))
        (note.i/delete! *db* (:note/id note))
        ;; The route also logs note/created and note/updated activity, whose
        ;; created_by is a not-null FK to user — left behind, it blocks the
        ;; fixture teardown from deleting this test's user.
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))

(deftest test-delete-removes-the-note
  (tf/testing "DELETE /taxon/:id/notes/:note-id/"
    (fixtures)
    (fn [{:keys [user taxon]}]
      (let [note (note.i/create! *db* {:body "written by mistake"
                                       :resource-type :taxon
                                       :resource-id (:taxon/id taxon)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url taxon))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url taxon) (:note/id note) "/")
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 200 (:status response)))
        (is (nil? (note.i/get-by-id *db* (:note/id note))))
        ;; The route also logs a note/created and a note/deleted activity,
        ;; whose created_by is a not-null FK to user — left behind, it blocks
        ;; the fixture teardown from deleting this test's user.
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))

(deftest test-a-note-on-another-taxon-is-not-reachable
  (tf/testing "DELETE with a note-id from a different taxon"
    (fixtures)
    (fn [{:keys [user taxon other-taxon]}]
      (let [note (note.i/create! *db* {:body "on the other taxon"
                                       :resource-type :taxon
                                       :resource-id (:taxon/id other-taxon)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url taxon))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url taxon) (:note/id note) "/")
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 404 (:status response))
            "Same resource type, different record — the id check must compare both")
        (is (some? (note.i/get-by-id *db* (:note/id note))))
        (note.i/delete! *db* (:note/id note))))))
