(ns sepal.app.routes.settings.users.activate-test
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

(defn- create-user! [db role password & {:keys [status] :or {status :active}}]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email :password password :role role :status status})
    email))

(deftest test-activate-success
  (testing "POST /settings/users/:id/activate activates an archived user"
    (let [password "testpassword123"
          admin-email (create-user! *db* :admin password)
          ;; Create an archived user
          archived-email (create-user! *db* :editor password :status :archived)
          archived-user (user.i/get-by-email *db* archived-email)
          _ (is (= :archived (:user/status archived-user)))
          sess (app.test/login admin-email password)
          {:keys [response] :as sess} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          url (str "/settings/users/" (:user/id archived-user) "/activate")
          {:keys [response]} (peri/request sess url
                                           :request-method :post
                                           :params {:__anti-forgery-token token})]
      (is (= 200 (:status response)))
      (let [updated-user (user.i/get-by-email *db* archived-email)]
        (is (= :active (:user/status updated-user)))))))

(deftest test-activate-shows-flash-message
  (testing "POST /settings/users/:id/activate shows success flash message"
    (let [password "testpassword123"
          admin-email (create-user! *db* :admin password)
          archived-email (create-user! *db* :editor password :status :archived)
          archived-user (user.i/get-by-email *db* archived-email)
          sess (app.test/login admin-email password)
          {:keys [response] :as sess} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          url (str "/settings/users/" (:user/id archived-user) "/activate")
          {:keys [response]} (peri/request sess url
                                           :request-method :post
                                           :headers {"hx-request" "true"}
                                           :params {:__anti-forgery-token token})]
      (is (= 200 (:status response)))
      (when (and (= 200 (:status response)) (string? (:body response)))
        (let [body (Jsoup/parse ^String (:body response))]
          (is (some? (flash-banner-text body))))))))
