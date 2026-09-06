(ns sepal.app.routes.material.detail.notes-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.note.interface :as note.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

;; note.i's readers gate on the schema version, so a direct call needs a
;; context. Requests get theirs from ::z/context.
(def ^:private ctx {:schema-version (db.i/latest-version)})

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

(defn- notes-url [material]
  (str "/material/" (:material/id material) "/notes/"))

(deftest test-post-creates-a-note
  (tf/testing "POST /material/:id/notes/"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [url (notes-url material)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :body "Found dead in the orchid nursery"})]
        (is (= 200 (:status response)))
        (let [notes (note.i/get-for-resource ctx *db* :material (:material/id material))]
          (is (= ["Found dead in the orchid nursery"] (mapv :note/body notes)))
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
    (fn [{:keys [user material]}]
      (let [url (notes-url material)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :body ""})]
        (is (= 422 (:status response)))
        (is (empty? (note.i/get-for-resource ctx *db* :material (:material/id material))))))))

(deftest test-get-lists-existing-notes
  (tf/testing "GET /material/:id/notes/"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [note (note.i/create! *db* {:body "moved to block 24"
                                       :resource-type :material
                                       :resource-id (:material/id material)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (notes-url material))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body (str "[data-note-id=" (:note/id note) "]"))))
        (is (some? (.selectFirst body "form#note-form")))
        (note.i/delete! *db* (:note/id note))))))

(deftest test-post-to-a-note-updates-it
  (tf/testing "POST /material/:id/notes/:note-id/"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [note (note.i/create! *db* {:body "befre"
                                       :resource-type :material
                                       :resource-id (:material/id material)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url material))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url material) (:note/id note) "/")
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
  (tf/testing "DELETE /material/:id/notes/:note-id/"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [note (note.i/create! *db* {:body "written by mistake"
                                       :resource-type :material
                                       :resource-id (:material/id material)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url material))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url material) (:note/id note) "/")
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 200 (:status response)))
        (is (nil? (note.i/get-by-id *db* (:note/id note))))
        ;; The route also logs a note/created and a note/deleted activity,
        ;; whose created_by is a not-null FK to user — left behind, it blocks
        ;; the fixture teardown from deleting this test's user.
        (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)})))))

(deftest test-a-note-belonging-to-another-resource-is-not-reachable
  (tf/testing "DELETE with a note-id from a different resource"
    (fixtures)
    (fn [{:keys [user taxon material]}]
      (let [note (note.i/create! *db* {:body "on the taxon"
                                       :resource-type :taxon
                                       :resource-id (:taxon/id taxon)
                                       :created-by (:user/id user)})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (notes-url material))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess
                                             (str (notes-url material) (:note/id note) "/")
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (= 404 (:status response)))
        (is (some? (note.i/get-by-id *db* (:note/id note))))
        (note.i/delete! *db* (:note/id note))))))
