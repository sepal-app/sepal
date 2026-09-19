(ns sepal.app.routes.settings.backups-unreachable-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* unreachable-backup-store-system-fixture]]
            [sepal.user.interface :as user.i]))

(use-fixtures :once unreachable-backup-store-system-fixture)

(defn- admin-session []
  (let [password "testpassword123"
        email (str "admin-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password password :role :admin})
    (app.test/login email password)))

(deftest test-unreachable-store-does-not-claim-there-are-no-backups
  (testing "a store that cannot be read is a different page from an empty one"
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/backups")]
      (is (= 200 (:status response))
          "the rest of the settings page still works")
      (is (not (.contains (:body response) "No backups yet"))
          "which would be a claim about the customer's data that is not true")
      (is (.contains (:body response) "could not be reached")))))
