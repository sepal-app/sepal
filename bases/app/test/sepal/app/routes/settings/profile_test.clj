(ns sepal.app.routes.settings.profile-test
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

(deftest test-profile-update
  (testing "POST /settings/profile with valid data updates profile and shows success message"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response] :as sess} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          {:keys [response] :as sess} (peri/request sess "/settings/profile"
                                                    :request-method :post
                                                    :params {:__anti-forgery-token token
                                                             :full_name "Updated Name"
                                                             :email email})
          _ (is (= 303 (:status response)) "Should redirect after successful update")
          {:keys [response]} (peri/follow-redirect sess)]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (= "Profile updated successfully" (flash-banner-text body)))))))
