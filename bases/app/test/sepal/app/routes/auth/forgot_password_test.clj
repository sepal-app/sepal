(ns sepal.app.routes.auth.forgot-password-test
  (:require [clojure.test :refer :all]
            [peridot.core :as peri]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(deftest forgot-password-get-test
  (testing "GET /forgot-password renders the form"
    (let [resp (*app* {:request-method :get
                       :uri "/forgot-password"})
          body (Jsoup/parse (:body resp))]
      (is (= 200 (:status resp)))
      (is (some? (-> body
                     (.selectFirst "input[name=email]"))))
      (is (some? (-> body
                     (.selectFirst "input[type=hidden][name=__anti-forgery-token]")))))))

(deftest forgot-password-post-existing-user-test
  (tf/testing "POST /forgot-password with existing user shows success message"
    {[::user.i/factory :key/user] {:db *db*
                                   :email "test@example.com"
                                   :password "12345678"}}
    (fn [{:keys [user]}]
      (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                            (peri/request "/forgot-password"))
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/forgot-password"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token token
                                                          :email (:user/email user)})
                                   (peri/follow-redirect))
            body (Jsoup/parse (:body response))]
        (is (= 200 (:status response)))
        ;; Should show success message (check your email)
        (is (some? (-> body (.selectFirst ".banner-text"))))
        (is (= "Check your email."
               (-> body (.selectFirst ".banner-text") (.text))))))))

(deftest forgot-password-post-unknown-email-test
  (testing "POST /forgot-password with unknown email still shows success (no enumeration)"
    (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                          (peri/request "/forgot-password"))
          token (test.i/response-anti-forgery-token response)
          {:keys [response]} (-> sess
                                 (peri/request "/forgot-password"
                                               :request-method :post
                                               :params {:__anti-forgery-token token
                                                        :email "nonexistent@example.com"})
                                 (peri/follow-redirect))
          body (Jsoup/parse (:body response))]
      (is (= 200 (:status response)))
      ;; Should show same success message to prevent email enumeration
      (is (= "Check your email."
             (-> body (.selectFirst ".banner-text") (.text)))))))
