(ns sepal.app.routes.auth.logout-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- logged-in? [sess]
  (= 200 (get-in (peri/request sess "/settings/profile") [:response :status])))

(deftest logout-test
  (tf/testing "logout"
    {[::user.i/factory :key/user] {:db *db*
                                   :email "logout@test.com"
                                   :password "password123"}}
    (fn [_]
      (testing "a GET does not log the user out"
        (let [sess (-> (app.test/login "logout@test.com" "password123")
                       (peri/request "/logout"))]
          (is (logged-in? sess))))

      (testing "the profile page's logout form posts, and the POST logs out"
        (let [sess (app.test/login "logout@test.com" "password123")
              {profile :response :as sess} (peri/request sess "/settings/profile")
              form (-> (app.test/parse-body profile) (.selectFirst "form[action=/logout]"))
              {:keys [response] :as sess} (peri/request sess "/logout"
                                                        :request-method :post
                                                        :params {:__anti-forgery-token
                                                                 (test.i/response-anti-forgery-token profile)})]
          (is (= "post" (some-> form (.attr "method"))))
          (is (= "/login" (get-in response [:headers "Location"])))
          (is (not (logged-in? sess))))))))
