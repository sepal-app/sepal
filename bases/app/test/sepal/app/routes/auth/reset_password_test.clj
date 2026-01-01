(ns sepal.app.routes.auth.reset-password-test
  "Tests for reset password functionality.
   Token encoding/decoding edge cases are covered by sepal.token.interface-test."
  (:require [clojure.test :refer :all]
            [peridot.core :as peri]
            [sepal.app.routes.auth.forgot-password :as forgot-password]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* *token-service* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- create-token [email]
  (forgot-password/reset-password-token *token-service* email))

;; =============================================================================
;; Happy Path Tests
;; =============================================================================

(deftest reset-password-full-flow-test
  (tf/testing "Full reset password flow: request -> receive email -> reset -> login"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "oldpassword1"}}
    (fn [{:keys [user]}]
      (let [;; Step 1: Request forgot password
            {:keys [response] :as sess} (-> (peri/session *app*)
                                            (peri/request "/forgot-password"))
            csrf-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/forgot-password"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token csrf-token
                                                          :email (:user/email user)})
                                   (peri/follow-redirect))
            body (Jsoup/parse (:body response))]
        ;; Forgot password should show success
        (is (= 200 (:status response)))
        (is (= "Check your email." (-> body (.selectFirst ".banner-text") (.text))))

        ;; Step 2: Use the reset token (simulating clicking email link)
        (let [token (create-token (:user/email user))
              {:keys [response] :as sess2} (-> (peri/session *app*)
                                               (peri/request (str "/reset-password?token=" token)))
              csrf-token2 (test.i/response-anti-forgery-token response)]
          (is (= 200 (:status response)))

          ;; Step 3: Submit new password
          (let [{:keys [response]} (-> sess2
                                       (peri/request "/reset-password"
                                                     :request-method :post
                                                     :params {:__anti-forgery-token csrf-token2
                                                              :token token
                                                              :password "newpassword123"
                                                              :confirm_password "newpassword123"})
                                       (peri/follow-redirect))
                body (Jsoup/parse (:body response))]
            (is (= 200 (:status response)))
            (is (= "Your password has been reset." (-> body (.selectFirst ".banner-text") (.text))))

            ;; Step 4: Verify new password works
            (is (some? (user.i/verify-password *db* (:user/email user) "newpassword123")))
            ;; Old password should not work
            (is (nil? (user.i/verify-password *db* (:user/email user) "oldpassword1")))))))))

;; =============================================================================
;; Invalid Token Handling
;; (Token encoding/decoding edge cases are tested in sepal.token.interface-test)
;; =============================================================================

(deftest reset-password-invalid-token-test
  (tf/testing "Reset password with invalid token fails gracefully"
    {[::user.i/factory :key/user] {:db *db*
                                   :password "oldpassword1"}}
    (fn [{:keys [user]}]
      (let [;; Use a tampered token (valid token + extra chars)
            invalid-token (str (create-token (:user/email user)) "tampered")
            {:keys [response] :as sess} (-> (peri/session *app*)
                                            (peri/request "/login"))
            csrf-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/reset-password"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token csrf-token
                                                          :token invalid-token
                                                          :password "newpassword123"
                                                          :confirm_password "newpassword123"})
                                   (peri/follow-redirect))
            body (Jsoup/parse (:body response))]
        (is (= 200 (:status response)))
        (is (= "Invalid password reset token." (-> body (.selectFirst ".banner-text") (.text))))
        ;; Password should NOT have changed
        (is (some? (user.i/verify-password *db* (:user/email user) "oldpassword1")))))))

;; =============================================================================
;; Security Tests
;; =============================================================================

(deftest forgot-password-no-email-enumeration-test
  (testing "Forgot password shows same message for unknown email (no enumeration)"
    (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                          (peri/request "/forgot-password"))
          csrf-token (test.i/response-anti-forgery-token response)
          {:keys [response]} (-> sess
                                 (peri/request "/forgot-password"
                                               :request-method :post
                                               :params {:__anti-forgery-token csrf-token
                                                        :email "nonexistent@example.com"})
                                 (peri/follow-redirect))
          body (Jsoup/parse (:body response))]
      (is (= 200 (:status response)))
      ;; Same success message as valid email to prevent enumeration
      (is (= "Check your email." (-> body (.selectFirst ".banner-text") (.text)))))))
