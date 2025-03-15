(ns sepal.app.routes.auth.login-test
  (:require [clojure.test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.matchers :refer [mismatch]]
            [matcher-combinators.test :refer [match?]]
            [peridot.core :as peri]
            [ring.middleware.session.store :as store]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture *cookie-store*]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]
            [sepal.user.interface.spec :as user.spec])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest login-test
  (let [db *db*]
    (testing "get"
      (let [resp (*app* {:request-method :get
                         :uri "/login"})
            body (Jsoup/parse (:body resp))]
        (is (= 200 (:status resp)))
        (is (some? (-> body
                       (.selectFirst "input[name=email]")
                       (.selectFirst "input[type=email]"))))
        (is (some? (-> body
                       (.selectFirst "input[name=password]")
                       (.selectFirst "input[type=password]"))))
        (is (some? (-> body
                       (.selectFirst "input[name=__anti-forgery-token]")
                       (.selectFirst "input[type=hidden]"))))))

    (tf/testing "post"
      {[::user.i/factory :key/user] {:db db
                                     :password "12345678"}}
      (fn [{:keys [user]}]
        (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                              (peri/request "/login"))
              token (test.i/response-anti-forgery-token response)
              params {:__anti-forgery-token token
                      :email (:user/email user)
                      :password "12345678"}
              {:keys [response]
               :as sess} (-> sess
                             (peri/request "/login"
                                           :request-method :post
                                           :params params)
                             (peri/follow-redirect)
                             (peri/follow-redirect))
              ring-session (->> (test.i/ring-session-cookie sess)
                                (store/read-session *cookie-store*))]
          (is (match? {:status 200
                       :headers {"content-type" "text/html"}}
                      response)
              (:body response))
          ;; Test that the user is in the session
          (is (match? user ring-session))
          ;; Eventually redirects to the activity page
          (is (= "Activity"
                 (-> response
                     :body
                     (Jsoup/parse)
                     (.selectFirst "main h1")
                     (.text)))))))

    (tf/testing "post - invalid password"
      {[::user.i/factory :key/user] {:db db
                                     :password "12345678"}}
      (fn [{:keys [user]}]
        (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                              (peri/request "/login"))
              token (test.i/response-anti-forgery-token response)
              params {:__anti-forgery-token token
                      :email (:user/email user)
                      :password (mg/generate user.spec/password)}
              {:keys [response]
               :as sess} (-> sess
                             (peri/request "/login"
                                           :request-method :post
                                           :params params)
                             ;; (peri/follow-redirect)
                             ;; (peri/follow-redirect)
                             (peri/follow-redirect))
              ring-session (->> (test.i/ring-session-cookie sess)
                                (store/read-session *cookie-store*))
              {:keys [status headers body]} response
              parsed-body (Jsoup/parse body)]
          (is (= status 200))
          (is (match? {"content-type" "text/html"}
                      headers))
          ;; Test that the user is in the session
          (is (match? (mismatch user) ring-session))
          ;; On error we redirect to the login page
          (is (= "/login" (-> parsed-body
                              (.selectFirst "form")
                              (.attr "action"))))
          ;; Shows the invalid password banner
          (is (= "Invalid password"
                 (-> parsed-body
                     (.selectFirst ".banner-text")
                     (.text)))))))))
