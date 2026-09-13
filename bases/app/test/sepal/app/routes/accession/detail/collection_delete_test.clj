(ns sepal.app.routes.accession.detail.collection-delete-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.collection.interface :as coll.i]
            [sepal.contact.interface :as contact.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::contact.i/factory :key/contact] {:db *db*}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db*
                                           :taxon (ig/ref :key/taxon)
                                           :contact (ig/ref :key/contact)}})

(defn- delete-url [accession]
  (str "/accession/" (:accession/id accession) "/collection/delete/"))

(defn- clear-activity! [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-clearing-the-collection-leaves-the-accession
  (tf/testing "POST /accession/:id/collection/delete/"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [id (:accession/id accession)
            collection (coll.i/create! *db* {:accession-id id
                                             :collector "A. Collector"})
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (delete-url accession))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess (delete-url accession)
                                             :request-method :post
                                             :params {:__anti-forgery-token token})]
        (try
          (is (= 303 (:status response)))
          (is (nil? (coll.i/get-by-id *db* (:collection/id collection))))
          (is (some? (accession.i/get-by-id *db* id))
              "clearing the collection is an edit of the accession, not a delete of it")
          (finally
            (clear-activity! user)))))))

(deftest test-no-collection-is-a-404
  (tf/testing "GET with nothing to clear"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url accession))]
        (is (= 404 (:status response))
            "there is no collection data on this accession")))))
