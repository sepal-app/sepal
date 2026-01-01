(ns sepal.app.routes.settings.organization-test
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

(deftest test-organization-update
  (testing "POST /settings/organization with valid data updates settings and shows success message"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response] :as sess} (peri/request sess "/settings/organization")
          token (test.i/response-anti-forgery-token response)
          {:keys [response] :as sess} (peri/request sess "/settings/organization"
                                                    :request-method :post
                                                    :params {:__anti-forgery-token token
                                                             :long_name "Test Organization"
                                                             :short_name "Test Org"
                                                             :abbreviation "TO"
                                                             :email "org@example.com"
                                                             :phone ""
                                                             :website ""
                                                             :address_street ""
                                                             :address_city ""
                                                             :address_postal_code ""
                                                             :address_country ""})
          _ (is (= 303 (:status response)) "Should redirect after successful update")
          {:keys [response]} (peri/follow-redirect sess)]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (= "Organization settings updated successfully" (flash-banner-text body)))))))
