(ns sepal.app.routes.tag.detail-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.tag.interface :as tag.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- post-tag [user tag params]
  (let [url (str "/tag/" (:tag/id tag) "/")
        sess (app.test/login (:user/email user) "testpassword123")
        {:keys [response] :as sess} (peri/request sess url)
        token (test.i/response-anti-forgery-token response)]
    (:response (peri/request sess url
                             :request-method :post
                             :headers {"hx-request" "true"}
                             :params (assoc params :__anti-forgery-token token)))))

(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}
   [::tag.i/factory :key/tag] {:db *db*}
   [::tag.i/factory :key/other] {:db *db*}})

(deftest test-a-tag-save-answers-with-the-page
  (tf/testing "saving a new name updates in place rather than redirecting to the index"
    (fixtures)
    (fn [{:keys [user tag]}]
      (try
        (let [response (post-tag user tag {:name "Renamed tag" :description ""})
              body (Jsoup/parse ^String (:body response))]
          (is (app.test/saved-in-place? response))
          (is (= "Renamed tag" (:tag/name (tag.i/get-by-id *db* (:tag/id tag)))))
          (is (.contains (.text (.selectFirst body ".spl-record")) "Renamed tag")))
        (finally
          (jdbc.sql/delete! *db* :activity {:created_by (:user/id user)}))))))

(deftest test-a-taken-tag-name-is-a-field-error
  (tf/testing "the duplicate answers 422 with the name's errors"
    (fixtures)
    (fn [{:keys [user tag other]}]
      (let [response (post-tag user tag {:name (:tag/name other) :description ""})]
        (is (= 422 (:status response)))
        (is (some? (.selectFirst (Jsoup/parse ^String (:body response)) "#name-errors")))))))
