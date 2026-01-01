(ns sepal.app.routes.settings.users.index-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- create-user! [db role password]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email :password password :role role :status :active})
    email))

(deftest test-users-index-page-loads
  (testing "GET /settings/users loads for admin"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response]} (peri/request sess "/settings/users")]
      (is (= 200 (:status response)) 
          (str "Expected 200, got " (:status response)))
      (when (= 200 (:status response))
        (let [body (Jsoup/parse ^String (:body response))]
          (is (some? (.selectFirst body "table"))
              "Page should contain a table"))))))
