(ns sepal.app.routes.propagation.detail-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.propagation.interface :as propagation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db*
                                 :password "testpassword123"
                                 :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::taxon.i/factory :key/rootstock] {:db *db*}
   [::accession.i/factory :key/acc] {:db *db*
                                     :taxon (ig/ref :key/taxon)}
   [::location.i/factory :key/loc] {:db *db*}
   [::propagation.i/factory :key/prop] {:db *db*
                                        :accession (ig/ref :key/acc)}})

(defn- page [user path]
  (let [sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response]} (-> sess (peri/request path))]
    (Jsoup/parse ^String (:body response))))

(deftest test-detail-renders-the-record
  (tf/testing "the detail page names the parent and offers the close-out"
    (fixtures)
    (fn [{:keys [user acc prop]}]
      (let [body (page user (str "/propagation/" (:propagation/id prop) "/"))]
        (is (some? (.selectFirst body (str "a[href='/accession/"
                                           (:accession/id acc) "/']")))
            "the parent accession is linked")
        (is (some? (.selectFirst body "button[value=complete]"))
            "an active batch can be closed out")
        (is (some? (.selectFirst body "button[value=failed]")))))))

(deftest test-graft-names-its-rootstock
  (tf/testing "a graft's rootstock is part of its identity"
    (fixtures)
    (fn [{:keys [user acc rootstock]}]
      (let [graft (propagation.i/create! *db* {:type :graft
                                               :parent-accession-id (:accession/id acc)
                                               :rootstock-taxon-id (:taxon/id rootstock)})]
        (try
          (let [body (page user (str "/propagation/" (:propagation/id graft) "/"))]
            (is (.contains (.text body) (:taxon/name rootstock))
                "the rootstock names the taxon"))
          (finally
            (jdbc.sql/delete! *db* :propagation {:id (:propagation/id graft)})))))))

(deftest test-panel-is-the-same-record
  (tf/testing "the list's slide-in shows the record"
    (fixtures)
    (fn [{:keys [user acc prop]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response]} (-> sess
                                   (peri/request (str "/propagation/"
                                                      (:propagation/id prop)
                                                      "/panel/")))]
        (is (= 200 (:status response)))
        (is (.contains ^String (:body response) (:accession/code acc)))))))
