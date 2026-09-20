(ns sepal.app.routes.settings.backups-empty-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* empty-backup-store-system-fixture]]
            [sepal.user.interface :as user.i]))

(use-fixtures :once empty-backup-store-system-fixture)

(defn- admin-session []
  (let [password "testpassword123"
        email (str "admin-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password password :role :admin})
    (app.test/login email password)))

(deftest test-empty-store-renders-no-backups-yet
  (testing "a store that answers with no rows is a different page from an unreachable one"
    (let [sess (admin-session)
          {:keys [response]} (peri/request sess "/settings/backups")]
      (is (= 200 (:status response)))
      (is (.contains (:body response) "No backups yet")))))
