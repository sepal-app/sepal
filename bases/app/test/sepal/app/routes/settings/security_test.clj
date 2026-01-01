(ns sepal.app.routes.settings.security-test
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
    (user.i/create! db {:email email :password password :role role})
    email))

(deftest test-password-change
  (testing "POST /settings/security with valid password changes password and shows success message"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response] :as sess} (peri/request sess "/settings/security")
          token (test.i/response-anti-forgery-token response)
          {:keys [response] :as sess} (peri/request sess "/settings/security"
                                                    :request-method :post
                                                    :params {:__anti-forgery-token token
                                                             :current_password password
                                                             :new_password "newpassword456"
                                                             :confirm_password "newpassword456"})
          _ (is (= 303 (:status response)) "Should redirect after successful password change")
          {:keys [response]} (peri/follow-redirect sess)]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (= "Password changed successfully" (flash-banner-text body)))))))

(deftest test-wrong-password
  (testing "POST /settings/security with wrong current password shows error message"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response] :as sess} (peri/request sess "/settings/security")
          token (test.i/response-anti-forgery-token response)
          {:keys [response] :as sess} (peri/request sess "/settings/security"
                                                    :request-method :post
                                                    :params {:__anti-forgery-token token
                                                             :current_password "wrongpassword"
                                                             :new_password "newpassword456"
                                                             :confirm_password "newpassword456"})
          _ (is (= 303 (:status response)) "Should redirect even on password error")
          {:keys [response]} (peri/follow-redirect sess)]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (= "Current password is incorrect" (flash-banner-text body)))))))
