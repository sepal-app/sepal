(ns sepal.app.routes.accession.delete-test
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
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- fixtures
  "A function, not a def: *db* is bound by the fixture at run time."
  ([] (fixtures :editor))
  ([role]
   {[::user.i/factory :key/user] {:db *db*
                                  :password "testpassword123"
                                  :role role}
    [::location.i/factory :key/location] {:db *db*}
    [::contact.i/factory :key/contact] {:db *db*}
    [::taxon.i/factory :key/taxon] {:db *db*}
    [::accession.i/factory :key/accession] {:db *db*
                                            :taxon (ig/ref :key/taxon)
                                            :contact (ig/ref :key/contact)}}))

(defn- delete-url [accession]
  (str "/accession/" (:accession/id accession) "/delete/"))

(defn- clear-activity! [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-get-returns-the-dialog
  (tf/testing "GET /accession/:id/delete/"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url accession))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body "dialog#delete_modal")))
        (is (some? (.selectFirst body "form[method=post]"))
            "nothing blocks this accession, so the delete is offered")))))

(deftest test-the-dialog-explains-a-blocker
  (tf/testing "GET with material attached"
    (assoc (fixtures)
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)})
    (fn [{:keys [user accession]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url accession))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (nil? (.selectFirst body "form[method=post]")))
        (is (re-find #"1 material" (.text body)))))))

(deftest test-post-deletes-and-redirects
  (tf/testing "POST /accession/:id/delete/"
    (fixtures)
    (fn [{:keys [user accession]}]
      (let [id (:accession/id accession)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (delete-url accession))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess (delete-url accession)
                                             :request-method :post
                                             :params {:__anti-forgery-token token})]
        (try
          (is (= 303 (:status response)))
          (is (= "/accession/" (get-in response [:headers "Location"])))
          (is (nil? (accession.i/get-by-id *db* id)))
          (finally
            (clear-activity! user)))))))

(deftest test-post-refuses-a-blocked-record
  (tf/testing "POST with material attached"
    (assoc (fixtures)
           [::material.i/factory :key/material] {:db *db*
                                                 :accession (ig/ref :key/accession)
                                                 :location (ig/ref :key/location)})
    (fn [{:keys [user accession]}]
      ;; The dialog offers no button here, so this is the hand-rolled request --
      ;; which is exactly the case the route must refuse on its own.
      (let [id (:accession/id accession)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (str "/accession/" id "/general/"))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess (delete-url accession)
                                             :request-method :post
                                             :params {:__anti-forgery-token token})]
        (is (= 422 (:status response)))
        (is (some? (accession.i/get-by-id *db* id)))))))

(deftest test-a-reader-cannot-delete
  (tf/testing "as a reader"
    (fixtures :reader)
    (fn [{:keys [user accession]}]
      (let [id (:accession/id accession)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url accession))]
        (is (= 302 (:status response)))
        (is (some? (accession.i/get-by-id *db* id))
            "a missing permission gate is invisible until someone finds it")))))
