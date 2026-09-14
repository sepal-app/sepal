(ns sepal.app.routes.material.delete-test
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
                                            :contact (ig/ref :key/contact)}
    [::material.i/factory :key/material] {:db *db*
                                          :accession (ig/ref :key/accession)
                                          :location (ig/ref :key/location)}}))

(defn- delete-url [material]
  (str "/material/" (:material/id material) "/delete/"))

(defn- clear-activity! [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(deftest test-get-returns-the-dialog
  (tf/testing "GET /material/:id/delete/"
    (fixtures)
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url material))
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (some? (.selectFirst body "form[method=post]"))
            "nothing blocks material")))))

(deftest test-deleting-material-takes-its-change-history
  (tf/testing "POST /material/:id/delete/"
    (assoc (fixtures)
           [::location.i/factory :key/destination] {:db *db*})
    (fn [{:keys [user material destination]}]
      (let [id (:material/id material)]
        (material.i/update! *db* id {:location-id (:location/id destination)})
        (is (seq (material.i/list-by-material-id *db* id))
            "a move wrote a change row")
        (let [sess (app.test/login (:user/email user) "testpassword123")
              {:keys [response] :as sess} (peri/request sess (delete-url material))
              token (test.i/response-anti-forgery-token response)
              {:keys [response]} (peri/request sess (delete-url material)
                                               :request-method :post
                                               :params {:__anti-forgery-token token})]
          (try
            (is (= 303 (:status response)))
            (is (nil? (material.i/get-by-id *db* id)))
            (is (empty? (material.i/list-by-material-id *db* id))
                "and the change rows went with it, by foreign key")
            (finally
              (clear-activity! user))))))))

(deftest test-a-reader-cannot-delete
  (tf/testing "as a reader"
    (fixtures :reader)
    (fn [{:keys [user material]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (peri/request sess (delete-url material))]
        (is (= 302 (:status response)))
        (is (some? (material.i/get-by-id *db* (:material/id material))))))))
