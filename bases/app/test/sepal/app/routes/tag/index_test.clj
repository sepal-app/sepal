(ns sepal.app.routes.tag.index-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.tag.interface :as tag.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-the-index-lists-tags-with-counts
  (tf/testing "an admin views the tag list"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            tag (tag.i/create! *db* {:name "Fruit"})
            {:keys [response]} (peri/request sess "/tag/")]
        (is (= 200 (:status response)))
        (is (re-find #"Fruit" (:body response)))
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-renaming-a-tag
  (tf/testing "PUT /tag/:id/ renames it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            tag (tag.i/create! *db* {:name "oct 16"})
            url (format "/tag/%s/" (:tag/id tag))
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :post
                                             :params {:__anti-forgery-token token
                                                      :name "October block"
                                                      :description ""})]
        (is (contains? #{200 303} (:status response)))
        (is (= "October block" (:tag/name (tag.i/get-by-id *db* (:tag/id tag)))))
        (tag.i/delete! *db* (:tag/id tag))))))

(deftest test-deleting-a-tag
  (tf/testing "DELETE removes it"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :admin}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            tag (tag.i/create! *db* {:name "sand tolerent"})
            url (format "/tag/%s/" (:tag/id tag))
            {:keys [response] :as sess} (peri/request sess url)
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess url
                                             :request-method :delete
                                             :headers {"x-csrf-token" token})]
        (is (contains? #{200 303} (:status response)))
        (is (nil? (tag.i/get-by-id *db* (:tag/id tag))))))))
