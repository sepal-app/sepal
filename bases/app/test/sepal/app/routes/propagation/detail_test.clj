(ns sepal.app.routes.propagation.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
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
            [sepal.test.interface :as test.i]
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
  (tf/testing "an editor gets the form, the panel and the actions menu"
    (fixtures)
    (fn [{:keys [user acc prop]}]
      (let [body (page user (str "/propagation/" (:propagation/id prop) "/"))]
        (is (some? (.selectFirst body "form#propagation-form")))
        (is (some? (.selectFirst body (str "a[href='/accession/"
                                           (:accession/id acc) "/']")))
            "the parent accession is linked")
        (is (some? (.selectFirst body "form[hx-post$=/status/] input[name=status][value=complete]"))
            "an active batch can be closed out from the menu")
        (is (some? (.selectFirst body "form[hx-post$=/status/] input[name=status][value=failed]")))
        (is (some? (.selectFirst body "form[hx-post$=/product/] input[name=kind]")))))))

(defn- clear-activity! [user]
  (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))

(defn- post-edit [user prop params]
  (let [path (str "/propagation/" (:propagation/id prop) "/")
        sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response] :as sess} (peri/request sess path)
        token (test.i/response-anti-forgery-token response)]
    (:response (peri/request sess path
                             :request-method :post
                             :params (assoc params :__anti-forgery-token token)))))

(deftest test-editing-a-batch
  (tf/testing "the form saves the counts entered after the batch was started"
    (fixtures)
    (fn [{:keys [user acc prop]}]
      (try
        (let [response (post-edit user prop {:type "cutting"
                                             :status "active"
                                             :parent-accession-id (str (:accession/id acc))
                                             :quantity-started "20"
                                             :quantity-succeeded "12"
                                             :succeeded-on "2026-05-01"
                                             :notes "Half in perlite"})
              saved (propagation.i/get-by-id *db* (:propagation/id prop))]
          (is (= 200 (:status response)))
          (is (= 20 (:propagation/quantity-started saved)))
          (is (= 12 (:propagation/quantity-succeeded saved)))
          (is (= "2026-05-01" (:propagation/succeeded-on saved)))
          (is (= "Half in perlite" (:propagation/notes saved)))
          (is (.contains (.text (page user (str "/propagation/" (:propagation/id prop) "/")))
                         "Half in perlite")
              "the panel shows the notes"))
        (finally
          (clear-activity! user))))))

(deftest test-editing-rejects-more-succeeded-than-started
  (tf/testing "the counts rule gets a field message"
    (fixtures)
    (fn [{:keys [user acc prop]}]
      (let [response (post-edit user prop {:type "cutting"
                                           :status "active"
                                           :parent-accession-id (str (:accession/id acc))
                                           :quantity-started "5"
                                           :quantity-succeeded "6"})]
        (is (= 422 (:status response)))
        (is (nil? (:propagation/quantity-started
                    (propagation.i/get-by-id *db* (:propagation/id prop)))))))))

(deftest test-the-parent-is-locked-once-there-are-products
  (tf/testing "a batch with products keeps its parent"
    (assoc (fixtures)
           [::accession.i/factory :key/other] {:db *db*
                                               :taxon (ig/ref :key/taxon)})
    (fn [{:keys [user acc other prop]}]
      (let [product (accession.i/create! *db* {:code (str "P-" (random-uuid))
                                               :taxon-id (:accession/taxon-id acc)
                                               :propagation-id (:propagation/id prop)})]
        (try
          (let [body (page user (str "/propagation/" (:propagation/id prop) "/"))]
            (is (some? (.selectFirst body "input[name=parent-accession-display][readonly]"))
                "the parent shows as read-only"))
          (post-edit user prop {:type "cutting"
                                :status "active"
                                :parent-accession-id (str (:accession/id other))})
          (is (= (:accession/id acc)
                 (:propagation/parent-accession-id
                   (propagation.i/get-by-id *db* (:propagation/id prop))))
              "a posted parent is ignored")
          (finally
            (accession.i/delete! *db* (:accession/id product))
            (clear-activity! user)))))))

(deftest test-the-parent-can-change-without-products
  (tf/testing "a batch with nothing recorded from it can move to another parent"
    (assoc (fixtures)
           [::accession.i/factory :key/other] {:db *db*
                                               :taxon (ig/ref :key/taxon)})
    (fn [{:keys [user acc other prop]}]
      (try
        (post-edit user prop {:type "cutting"
                              :status "active"
                              :parent-accession-id (str (:accession/id other))})
        (is (= (:accession/id other)
               (:propagation/parent-accession-id
                 (propagation.i/get-by-id *db* (:propagation/id prop)))))
        (finally
          ;; Back on its own parent, so the fixtures tear down in order.
          (propagation.i/update! *db* (:propagation/id prop)
                                 {:parent-accession-id (:accession/id acc)})
          (clear-activity! user))))))

(deftest test-reader-sees-the-record-without-the-form
  (tf/testing "a reader gets the panel as a page"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :reader}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
     [::propagation.i/factory :key/prop] {:db *db*
                                          :accession (ig/ref :key/acc)}}
    (fn [{:keys [user prop]}]
      (let [body (page user (str "/propagation/" (:propagation/id prop) "/"))]
        (is (nil? (.selectFirst body "form#propagation-form")))
        (is (nil? (.selectFirst body "form[hx-post$=/status/]")))
        (is (.contains (.text body) "History"))))))

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

(deftest test-detail-shows-labels-and-history
  (tf/testing "the method and status read as labels, and the history is filled in"
    (fixtures)
    (fn [{:keys [user acc]}]
      (let [prop (propagation.i/create! *db* {:type :tissue_culture
                                              :parent-accession-id (:accession/id acc)
                                              :propagated-on "2026-03-01"
                                              :quantity-started 20
                                              :quantity-succeeded 12})]
        (try
          (let [text (.text (page user (str "/propagation/" (:propagation/id prop) "/")))]
            (is (.contains text "Tissue culture · In progress"))
            (is (not (.contains text ":tissue_culture")))
            (is (.contains text "2026-03-01"))
            (is (.contains text "20"))
            (is (.contains text "12")))
          (finally
            (jdbc.sql/delete! *db* :propagation {:id (:propagation/id prop)})))))))

(deftest test-reader-cannot-close-out-a-batch
  (tf/testing "a reader's POST leaves the status alone"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "testpassword123"
                                   :role :reader}
     [::taxon.i/factory :key/taxon] {:db *db*}
     [::accession.i/factory :key/acc] {:db *db*
                                       :taxon (ig/ref :key/taxon)}
     [::propagation.i/factory :key/prop] {:db *db*
                                          :accession (ig/ref :key/acc)}}
    (fn [{:keys [user prop]}]
      (let [path (str "/propagation/" (:propagation/id prop) "/")
            sess (app.test/login (:user/email user) "testpassword123")
            ;; A reader's detail page has no form, so the token comes off one
            ;; they can use.
            {:keys [response] :as sess} (peri/request sess "/settings/profile")
            token (test.i/response-anti-forgery-token response)]
        (is (some? token))
        (is (= 200 (-> (peri/request sess path
                                     :request-method :post
                                     :params {:__anti-forgery-token token
                                              :status "complete"})
                       :response
                       :status))
            "the POST gets past the anti-forgery check")
        (is (= :active (:propagation/status
                         (propagation.i/get-by-id *db* (:propagation/id prop)))))))))

(deftest test-editing-rejects-success-before-the-sowing
  (tf/testing "the edit form refuses a succeeded date before the propagated one"
    (fixtures)
    (fn [{:keys [user acc prop]}]
      (let [response (post-edit user prop {:type "cutting"
                                           :status "active"
                                           :parent-accession-id (str (:accession/id acc))
                                           :propagated-on "2026-03-01"
                                           :succeeded-on "2026-02-01"})]
        (is (= 422 (:status response)))
        (is (nil? (:propagation/succeeded-on
                    (propagation.i/get-by-id *db* (:propagation/id prop)))))))))

(deftest test-deleting-a-propagation
  (tf/testing "the actions menu deletes a batch with nothing produced"
    (fixtures)
    (fn [{:keys [user acc]}]
      (let [prop (propagation.i/create! *db* {:type :seed
                                              :parent-accession-id (:accession/id acc)})
            path (str "/propagation/" (:propagation/id prop) "/")
            sess (app.test/login (:user/email user) "testpassword123")
            {:keys [response] :as sess} (peri/request sess path)
            body (Jsoup/parse ^String (:body response))
            token (test.i/response-anti-forgery-token response)]
        (try
          (is (some? (.selectFirst body (str "[hx-get='" path "delete/']")))
              "the menu offers Delete")
          (let [{:keys [response]} (peri/request sess (str path "delete/")
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token})]
            (is (= 303 (:status response)))
            (is (nil? (propagation.i/get-by-id *db* (:propagation/id prop)))))
          (finally
            (clear-activity! user)
            (jdbc.sql/delete! *db* :propagation {:id (:propagation/id prop)})))))))
