(ns sepal.app.routes.accession.detail.notes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.note.interface :as note.i]
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
   [::accession.i/factory :key/accession] {:db *db*
                                           :taxon (ig/ref :key/taxon)
                                           :contact (ig/ref :key/contact)}})

(defn- notes-url [accession]
  (str "/accession/" (:accession/id accession) "/notes/"))

(deftest test-post-creates-a-note
  (tf/testing "POST /accession/:id/notes/"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [url (notes-url accession)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :body "Reaccessioned, lost original acc #"})]
        (is (= 200 (:status response)))
        (let [notes (note.i/get-for-resource *db* :accession (:accession/id accession))]
          (is (= ["Reaccessioned, lost original acc #"] (mapv :note/body notes)))
          (is (= (:user/id user) (:note/created-by (first notes))))
          (testing "the response is the swapped list, carrying the new note"
            (let [body (Jsoup/parse ^String (:body response))]
              (is (some? (.selectFirst body "#notes-list")))
              (is (= 1 (.size (.select body "[data-note-id]"))))))
          (note.i/delete! *db* (:note/id (first notes)))
          ;; The route also logs a note/created activity, whose created_by is
          ;; a not-null FK to user — left behind, it blocks the fixture
          ;; teardown from deleting this test's user.
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-post-with-an-empty-body-is-rejected
  (tf/testing "POST with a blank body"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [url (notes-url accession)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :body ""})]
        (is (= 422 (:status response)))
        (is (empty? (note.i/get-for-resource *db* :accession (:accession/id accession)))
            "An empty note is not a note")))))

(deftest test-get-lists-existing-notes
  (tf/testing "GET /accession/:id/notes/"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [note (note.i/create! *db* {:body "found dead in the orchid nursery"
                                       :resource-type :accession
                                       :resource-id (:accession/id accession)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (notes-url accession))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body (str "[data-note-id=" (:note/id note) "]"))))
        (is (some? (.selectFirst body "form#note-form")))
        (note.i/delete! *db* (:note/id note))))))

(deftest test-post-to-a-note-updates-it
  (tf/testing "POST /accession/:id/notes/:note-id/"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [note (note.i/create! *db* {:body "befre"
                                       :resource-type :accession
                                       :resource-id (:accession/id accession)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url accession))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url accession) (:note/id note) "/")
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
  (tf/testing "DELETE /accession/:id/notes/:note-id/"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [note (note.i/create! *db* {:body "written by mistake"
                                       :resource-type :accession
                                       :resource-id (:accession/id accession)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url accession))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url accession) (:note/id note) "/")
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 200 (:status response)))
        (is (nil? (note.i/get-by-id *db* (:note/id note))))
        ;; The route also logs a note/created and a note/deleted activity,
        ;; whose created_by is a not-null FK to user — left behind, it blocks
        ;; the fixture teardown from deleting this test's user.
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))

(deftest test-a-note-belonging-to-another-resource-is-not-reachable
  (tf/testing "DELETE with a note-id from a different accession"
    (fixtures)
    (fn [{:keys [user taxon accession]}]
      ;; The note is on the taxon; the URL says accession. Without a check that
      ;; the note belongs to the resource in the path, any note in the garden is
      ;; deletable through any resource's URL.
      (let [note (note.i/create! *db* {:body "on the taxon"
                                       :resource-type :taxon
                                       :resource-id (:taxon/id taxon)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url accession))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url accession) (:note/id note) "/")
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 404 (:status response)))
        (is (some? (note.i/get-by-id *db* (:note/id note))))
        (note.i/delete! *db* (:note/id note))))))

(deftest test-a-reader-cannot-reach-the-tab
  (tf/testing "GET as a reader"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :reader}
     [::contact.i/factory :key/contact] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db*
                                             :taxon (ig/ref :key/taxon)
                                             :contact (ig/ref :key/contact)}}
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (notes-url accession))]
        (is (= 302 (:status response))
            "A reader is redirected to the panel, which Task 8 fills in")))))
