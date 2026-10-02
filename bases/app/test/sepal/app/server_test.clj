(ns sepal.app.server-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.routes.activity.index :as activity.index]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.database.interface :as db.i]
            [sepal.user.interface :as user.i]
            [taoensso.telemere :as t]))

(use-fixtures :once default-system-fixture)

(deftest sqlite-pragma-test
  (testing "journal_mode is WAL"
    (is (= [{:journal-mode "wal"}]
           (db.i/execute! *db* ["PRAGMA journal_mode"]))))

  (testing "foreign_keys is enabled"
    (is (= [{:foreign-keys 1}]
           (db.i/execute! *db* ["PRAGMA foreign_keys"])))))

(deftest test-an-unhandled-exception-is-logged-not-shown
  ;; The stack trace used to go to the browser and nowhere else: class names,
  ;; file paths and whatever the message held, and nothing in the log.
  (let [email (str "error-" (random-uuid) "@test.com")
        _ (user.i/create! *db* {:email email :password "testpassword123" :role :admin})
        sess (app.test/login email "testpassword123")
        boom (fn [& _] (throw (ex-info "secret-detail-from-the-server" {})))]
    (with-redefs [activity.index/handler boom]
      (testing "a page request gets a plain 500 page"
        (let [{:keys [value signals]} (t/with-signals (:response (peri/request sess "/activity")))
              response value]
          (is (= 500 (:status response)))
          (is (not (str/includes? (:body response) "secret-detail-from-the-server")))
          (is (not (str/includes? (:body response) "clojure.lang")))
          (is (str/includes? (:body response) "Something went wrong"))
          ;; zodiac logs it with (log/error e), so the exception, trace and
          ;; all, arrives as the signal's message.
          (is (some #(and (= :error (:level %))
                          (str/includes? (str (force (:msg_ %))) "secret-detail-from-the-server"))
                    signals)
              "the exception is logged")))
      (testing "an htmx request gets an error banner it can show"
        (let [{:keys [response]} (peri/request sess "/activity" :headers {"hx-request" "true"})]
          (is (= 500 (:status response)))
          (is (str/includes? (:body response) "flash-container"))
          (is (str/includes? (:body response) "Something went wrong"))
          (is (not (str/includes? (:body response) "secret-detail-from-the-server"))))))))
