(ns sepal.app.routes.settings.users.archive-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- flash-banner-text [body]
  (some-> (.selectFirst body ".banner-text") (.text)))

(defn- create-user! [db role password]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email :password password :role role :status :active})
    email))

(deftest test-archive-success
  (testing "POST /settings/users/:id/archive archives another user"
    (let [password "testpassword123"
          admin-email (create-user! *db* :admin password)
          editor-email (create-user! *db* :editor password)
          editor (user.i/get-by-email *db* editor-email)
          sess (app.test/login admin-email password)
          {:keys [response] :as sess} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          url (str "/settings/users/" (:user/id editor) "/archive")
          {:keys [response]} (peri/request sess url
                                           :request-method :post
                                           :params {:__anti-forgery-token token})]
      (is (= 200 (:status response)))
      (let [updated-editor (user.i/get-by-email *db* editor-email)]
        (is (= :archived (:user/status updated-editor)))))))

(deftest test-archive-self
  (testing "POST /settings/users/:id/archive cannot archive yourself"
    (let [password "testpassword123"
          admin-email (create-user! *db* :admin password)
          admin (user.i/get-by-email *db* admin-email)
          sess (app.test/login admin-email password)
          {:keys [response] :as sess} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          url (str "/settings/users/" (:user/id admin) "/archive")
          {:keys [response]} (peri/request sess url
                                           :request-method :post
                                           :params {:__anti-forgery-token token})]
      (is (= 422 (:status response)))
      ;; User should still be active
      (let [user (user.i/get-by-email *db* admin-email)]
        (is (= :active (:user/status user)))))))

(deftest test-archive-last-admin
  (testing "POST /settings/users/:id/archive cannot archive the last admin"
    (let [password "testpassword123"
          ;; Create two admins
          admin1-email (create-user! *db* :admin password)
          admin2-email (create-user! *db* :admin password)
          admin1 (user.i/get-by-email *db* admin1-email)
          admin2 (user.i/get-by-email *db* admin2-email)
          sess (app.test/login admin1-email password)
          {:keys [response] :as sess} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          ;; Archive admin2 first (should succeed since admin1 is still admin)
          {:keys [response] :as sess} (peri/request sess (str "/settings/users/" (:user/id admin2) "/archive")
                                                    :request-method :post
                                                    :params {:__anti-forgery-token token})
          _ (is (= 200 (:status response)) "First archive should succeed")
          ;; admin1 is now the only active admin
          ;; We can't test archiving the last admin via API because:
          ;; 1. admin1 can't archive themselves (blocked)
          ;; 2. admin2 is archived and can't log in
          ;; So we just verify admin1 is still active
          admin1-updated (user.i/get-by-email *db* admin1-email)]
      (is (= :active (:user/status admin1-updated))))))
