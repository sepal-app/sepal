(ns sepal.app.e2e.error-page-test
  "E2E coverage for what a curator sees when a request fails on the server."
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.e2e.playwright :as pw]
            [sepal.app.e2e.server :as server]
            [sepal.app.routes.activity.index :as activity.index]
            [sepal.app.test.email :as test.email]
            [sepal.user.interface :as user.i]))

(deftest ^:e2e a-server-error-shows-a-banner
  (testing "an htmx request that fails on the server raises an error banner"
    (server/with-server
      (fn [started]
        (let [base-url (server/server-url started)
              db (server/db started)
              email (test.email/unique)
              password "TestPassword123!"]
          (user.i/create! db {:email email :password password :role :admin})
          (pw/with-browser
            (pw/navigate (str base-url "/login"))
            (pw/wait-for-selector "input[name=\"email\"]" 10000)
            (pw/fill "input[name=\"email\"]" email)
            (pw/fill "input[name=\"password\"]" password)
            (pw/click "button:has-text(\"Login\")")
            (pw/wait-for-url #"/activity" 60000)
            ;; HTMX swaps nothing for a 5xx by default, so without page.ts
            ;; applying the response's out-of-band banner the request failed
            ;; silently.
            (with-redefs [activity.index/handler (fn [& _] (throw (ex-info "boom" {})))]
              (pw/evaluate "htmx.ajax('GET', '/activity', {target: 'body', swap: 'none'})")
              (pw/wait-for-selector "#flash-container .spl-banner" 10000)
              (is (re-find #"Something went wrong"
                           (pw/text-content "#flash-container .spl-banner")))
              (is (some? (pw/evaluate "document.getElementById('page-region')"))
                  "the page is still there"))))))))
