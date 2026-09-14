(ns sepal.app.routes.contact.delete-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.contact.interface :as contact.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- delete-url [contact]
  (str "/contact/" (:contact/id contact) "/delete/"))

(defn- clear-activity! [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-a-contact-supplying-an-accession-is-blocked
  ;; The accession factory takes :contact, so attaching one is the default
  ;; shape of a supplier relationship.
  (tf/testing "GET /contact/:id/delete/ with an accession"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::contact.i/factory :key/contact] {:db *db*}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/accession] {:db *db*
                                             :taxon (ig/ref :key/taxon)
                                             :contact (ig/ref :key/contact)}}
    (fn [{:keys [user contact]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url contact))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (nil? (.selectFirst body "form[method=post]")))
        (is (re-find #"1 accession" (.text body)))))))

(deftest test-a-contact-with-no-accession-deletes
  (tf/testing "POST /contact/:id/delete/"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :editor}
     [::contact.i/factory :key/contact] {:db *db*}}
    (fn [{:keys [user contact]}]
      (let [id (:contact/id contact)
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess (delete-url contact))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess (delete-url contact)
                                             :request-method :post
                                             :params {:__anti-forgery-token token})]
        (try
          (is (= 303 (:status response)))
          (is (nil? (contact.i/get-by-id *db* id)))
          (finally
            (clear-activity! user)))))))

(deftest test-a-reader-cannot-delete
  (tf/testing "as a reader"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :reader}
     [::contact.i/factory :key/contact] {:db *db*}}
    (fn [{:keys [user contact]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url contact))]
        (is (= 302 (:status response)))
        (is (some? (contact.i/get-by-id *db* (:contact/id contact))))))))
