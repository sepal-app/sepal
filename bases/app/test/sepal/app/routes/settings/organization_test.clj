(ns sepal.app.routes.settings.organization-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.backup.core :as backup]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.settings.interface :as settings.i]
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
                                                             :address_country ""
                                                             :timezone "America/New_York"})
          _ (is (= 303 (:status response)) "Should redirect after successful update")
          {:keys [response]} (peri/follow-redirect sess)]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (= "Organization settings updated successfully" (flash-banner-text body)))))))

(defn- post-organization [sess timezone]
  (let [{:keys [response] :as sess} (peri/request sess "/settings/organization")
        token (test.i/response-anti-forgery-token response)]
    (:response (peri/request sess "/settings/organization"
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
                                      :address_country ""
                                      :timezone timezone}))))

(deftest test-an-unknown-timezone-is-refused
  (let [password "testpassword123"
        sess (app.test/login (create-user! *db* :admin password) password)
        before (settings.i/get-value *db* "organization.timezone")
        response (post-organization sess "America/Belise")]
    (is (= 422 (:status response)) "a misspelt zone would break every page that shows a time")
    (is (= before (settings.i/get-value *db* "organization.timezone")))))

(deftest test-a-bad-stored-timezone-does-not-break-pages
  (testing "a zone stored before validation existed reads as UTC"
    (let [password "testpassword123"
          sess (app.test/login (create-user! *db* :admin password) password)]
      (doseq [stored ["America/Belise" ""]]
        (settings.i/set-value! *db* "organization.timezone" stored)
        (try
          (is (= 200 (:status (:response (peri/request sess "/activity"))))
              (pr-str stored))
          (finally
            (settings.i/set-value! *db* "organization.timezone" "UTC")))))))

(deftest test-a-new-timezone-reschedules-backups
  ;; Backups run at 2 AM garden time, and the job's times are fixed when it is
  ;; registered, so a new zone would not move it until a restart.
  (let [password "testpassword123"
        sess (app.test/login (create-user! *db* :admin password) password)
        registered (atom 0)]
    (try
      (with-redefs [backup/register-backup-job! (fn [& _] (swap! registered inc))]
        (is (= 303 (:status (post-organization sess "America/Belize")))))
      (is (= 1 @registered))
      (finally
        (settings.i/set-value! *db* "organization.timezone" "UTC")))))
