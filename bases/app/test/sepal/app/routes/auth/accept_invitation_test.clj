(ns sepal.app.routes.auth.accept-invitation-test
  "Tests for accepting user invitations."
  (:require [clojure.test :refer :all]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*app* *db* *token-service* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(defn- create-invitation-token [email & {:keys [full-name expires-at]}]
  (token.i/encode *token-service*
                  {:email email
                   :full-name full-name
                   :expires-at (or expires-at (token.i/expires-in-hours 24))}))

;; =============================================================================
;; Accept Invitation Tests
;; =============================================================================

(deftest accept-invitation-success-test
  (tf/testing "Invited user can set password and activate account"
    {[::user.i/factory :key/invited-user] {:db *db*
                                           :status :invited
                                           :full-name nil}}
    (fn [{:keys [invited-user]}]
      (let [email (:user/email invited-user)
            token (create-invitation-token email :full-name "New Name")
            ;; GET the accept form
            {:keys [response] :as sess} (-> (peri/session *app*)
                                            (peri/request (str "/accept-invitation?token=" token)))
            _ (is (= 200 (:status response)))
            csrf-token (test.i/response-anti-forgery-token response)
            ;; Verify form shows email
            _ (is (app.test/body-contains? response email))
            ;; Submit password
            {:keys [response]} (-> sess
                                   (peri/request "/accept-invitation"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token csrf-token
                                                          :token token
                                                          :full-name "New Name"
                                                          :password "newpassword123"
                                                          :confirm-password "newpassword123"})
                                   (peri/follow-redirect))]
        ;; Should redirect to login with success message
        (is (= 200 (:status response)))
        (is (app.test/body-contains? response "Password set"))
        ;; User should now be active
        (let [user (user.i/get-by-id *db* (:user/id invited-user))]
          (is (= :active (:user/status user)))
          (is (= "New Name" (:user/full-name user))))
        ;; Password should work
        (is (some? (user.i/verify-password *db* email "newpassword123")))))))

(deftest accept-invitation-prefills-name-test
  (tf/testing "Accept form prefills full name from user record"
    {[::user.i/factory :key/invited-user] {:db *db*
                                           :status :invited
                                           :full-name "Existing Name"}}
    (fn [{:keys [invited-user]}]
      (let [email (:user/email invited-user)
            token (create-invitation-token email)
            {:keys [response]} (-> (peri/session *app*)
                                   (peri/request (str "/accept-invitation?token=" token)))
            body (app.test/parse-body response)
            name-input (-> body (.selectFirst "input[name=full-name]"))]
        (is (= 200 (:status response)))
        (is (some? name-input))
        (is (= "Existing Name" (.val name-input)))))))

(deftest accept-invitation-invalid-token-test
  (tf/testing "Invalid token redirects with error"
    {}
    (fn [_]
      (let [{:keys [response]} (-> (peri/session *app*)
                                   (peri/request "/accept-invitation?token=invalid-token")
                                   (peri/follow-redirect))]
        (is (= 200 (:status response)))
        (is (app.test/body-contains? response "Invalid invitation"))))))

(deftest accept-invitation-expired-token-test
  (tf/testing "Expired token redirects with error"
    {[::user.i/factory :key/invited-user] {:db *db*
                                           :status :invited}}
    (fn [{:keys [invited-user]}]
      (let [email (:user/email invited-user)
            ;; Create token that expired 1 second ago
            token (create-invitation-token email :expires-at (- (token.i/expires-in 0) 1))
            {:keys [response]} (-> (peri/session *app*)
                                   (peri/request (str "/accept-invitation?token=" token))
                                   (peri/follow-redirect))]
        (is (= 200 (:status response)))
        (is (app.test/body-contains? response "Invalid invitation"))))))

(deftest accept-invitation-already-active-test
  (tf/testing "Already active user is redirected to login"
    {[::user.i/factory :key/active-user] {:db *db*
                                          :status :active}}
    (fn [{:keys [active-user]}]
      (let [email (:user/email active-user)
            token (create-invitation-token email)
            {:keys [response]} (-> (peri/session *app*)
                                   (peri/request (str "/accept-invitation?token=" token))
                                   (peri/follow-redirect))]
        (is (= 200 (:status response)))
        (is (app.test/body-contains? response "already activated"))))))

(deftest accept-invitation-archived-user-test
  (tf/testing "Archived user gets invalid invitation error"
    {[::user.i/factory :key/archived-user] {:db *db*
                                            :status :archived}}
    (fn [{:keys [archived-user]}]
      (let [email (:user/email archived-user)
            token (create-invitation-token email)
            {:keys [response]} (-> (peri/session *app*)
                                   (peri/request (str "/accept-invitation?token=" token))
                                   (peri/follow-redirect))]
        (is (= 200 (:status response)))
        (is (app.test/body-contains? response "Invalid invitation"))))))

(deftest accept-invitation-passwords-must-match-test
  (tf/testing "Passwords must match"
    {[::user.i/factory :key/invited-user] {:db *db*
                                           :status :invited}}
    (fn [{:keys [invited-user]}]
      (let [email (:user/email invited-user)
            token (create-invitation-token email)
            {:keys [response] :as sess} (-> (peri/session *app*)
                                            (peri/request (str "/accept-invitation?token=" token)))
            csrf-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/accept-invitation"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token csrf-token
                                                          :token token
                                                          :password "password123"
                                                          :confirm-password "different123"}))]
        ;; Should stay on form with error
        (is (= 200 (:status response)))
        (is (app.test/body-contains? response "do not match"))
        ;; User should still be invited
        (is (= :invited (:user/status (user.i/get-by-id *db* (:user/id invited-user)))))))))
