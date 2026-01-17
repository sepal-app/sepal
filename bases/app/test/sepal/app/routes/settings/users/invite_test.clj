(ns sepal.app.routes.settings.users.invite-test
  "Tests for user invitation functionality."
  (:require [clojure.test :refer :all]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* *mail-client* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

;; =============================================================================
;; Invite User Tests
;; =============================================================================

(deftest invite-user-success-test
  (tf/testing "Admin can invite a new user"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :password "adminpass1"}}
    (fn [{:keys [admin]}]
      (let [;; Login as admin
            sess (app.test/login (:user/email admin) "adminpass1")
            ;; Get the invite form
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/settings/users/invite"))
            _ (is (= 200 (:status response)))
            csrf-token (test.i/response-anti-forgery-token response)
            ;; Submit invitation
            new-email "newuser@example.com"
            {:keys [response] :as sess2} (-> sess
                                             (peri/request "/settings/users/invite"
                                                           :request-method :post
                                                           :params {:__anti-forgery-token csrf-token
                                                                    :email new-email
                                                                    :role "editor"
                                                                    :full-name "New User"}))
            _ (is (= 303 (:status response)) "Should redirect after successful invite")
            {:keys [response]} (peri/follow-redirect sess2)]
        ;; Should redirect to users index
        (is (= 200 (:status response)))
        ;; User should be created with invited status
        (let [new-user (user.i/get-by-email *db* new-email)]
          (is (some? new-user))
          (is (= :invited (:user/status new-user)))
          (is (= :editor (:user/role new-user)))
          (is (= "New User" (:user/full-name new-user))))
        ;; Email should have been sent
        (is (= 1 (count @(:sent-messages *mail-client*))))
        (let [sent-email (first @(:sent-messages *mail-client*))]
          (is (= new-email (:to sent-email)))
          (is (.contains (:body sent-email) "accept-invitation")))))))

(deftest invite-duplicate-email-test
  (tf/testing "Cannot invite user with existing email"
    {[::user.i/factory :key/admin] {:db *db*
                                    :role :admin
                                    :password "adminpass1"}
     [::user.i/factory :key/existing] {:db *db*
                                       :role :reader}}
    (fn [{:keys [admin existing]}]
      (let [sess (app.test/login (:user/email admin) "adminpass1")
            {:keys [response] :as sess} (-> sess
                                            (peri/request "/settings/users/invite"))
            csrf-token (test.i/response-anti-forgery-token response)
            {:keys [response]} (-> sess
                                   (peri/request "/settings/users/invite"
                                                 :request-method :post
                                                 :params {:__anti-forgery-token csrf-token
                                                          :email (:user/email existing)
                                                          :role "editor"}))]
        ;; Should show error on the form (not redirect)
        (is (= 200 (:status response)))
        (is (app.test/body-contains? response "already registered"))))))

(deftest invite-requires-admin-test
  (tf/testing "Non-admin cannot access invite page"
    {[::user.i/factory :key/editor] {:db *db*
                                     :role :editor
                                     :password "editorpass1"}}
    (fn [{:keys [editor]}]
      (let [sess (app.test/login (:user/email editor) "editorpass1")
            {:keys [response]} (-> sess
                                   (peri/request "/settings/users/invite"))]
        ;; Should be forbidden or redirect
        (is (or (= 403 (:status response))
                (= 302 (:status response))))))))
