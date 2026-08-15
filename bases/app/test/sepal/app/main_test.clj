(ns sepal.app.main-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [hato.client :as http]
            [malli.core :as m]
            [sepal.app.instance :as instance]
            [sepal.app.main :as main])
  (:import [java.net ServerSocket]))

(deftest test-env-opts-satisfy-the-instance-schemas
  (testing "the opts built from the environment are valid, so self-hosted boot
            cannot fail on a schema the library enforces"
    (let [{:keys [process instance]} (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                                     "COOKIE_SECRET" "1234567890123456"
                                                     "APP_DOMAIN" "sepal.example.org"})]
      (is (m/validate instance/ProcessOpts process)
          (str "invalid process opts: " (pr-str process)))
      (is (m/validate instance/InstanceOpts instance)
          (str "invalid instance opts: " (pr-str instance))))))

(deftest test-a-missing-cookie-secret-is-refused
  (testing "an unset COOKIE_SECRET fails loudly rather than defaulting to a
            master secret that is published in this repository"
    (let [thrown (try
                   (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"})
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (some? thrown) "env-opts should have thrown")
      (is (= :missing-cookie-secret (:reason (ex-data thrown))))
      (is (re-find #"COOKIE_SECRET" (ex-message thrown))
          "the message must name the variable the operator has to set")))

  (testing "an empty COOKIE_SECRET is the same as an unset one"
    (let [thrown (try
                   (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                   "COOKIE_SECRET" ""})
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :missing-cookie-secret (:reason (ex-data thrown)))))))

(deftest test-self-hosted-uses-one-fixed-slug-and-database
  (testing "a self-hosted install is one garden under SEPAL_DATA_HOME"
    (let [{:keys [instance]} (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                             "COOKIE_SECRET" "1234567890123456"})]
      (is (= "self-hosted" (:slug instance)))
      (is (= "/tmp/sepal-selfhosted/sepal.db" (:db-path instance)))
      (is (= "/tmp/sepal-selfhosted/backups" (:backup-dir instance)))
      (is (= "/tmp/sepal-selfhosted/cache" (:media-cache-dir instance))))))

(deftest test-env-opts-honours-host-cache-size-and-email-vars
  (testing "env vars system.edn used to read are not silently dropped by env-opts"
    (let [{:keys [instance]} (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                             "COOKIE_SECRET" "1234567890123456"
                                             "HOST" "127.0.0.1"
                                             "IMAGE_CACHE_SIZE_MB" "250"
                                             "FORGOT_PASSWORD_EMAIL_FROM" "reset@example.org"
                                             "FORGOT_PASSWORD_EMAIL_SUBJECT" "Reset it"
                                             "INVITATION_EMAIL_FROM" "invite@example.org"
                                             "INVITATION_EMAIL_SUBJECT" "You're in"})]
      (is (= "127.0.0.1" (:jetty-host instance)))
      (is (= 250 (:media-cache-size-mb instance)))
      (is (= "reset@example.org" (:forgot-password-email-from instance)))
      (is (= "Reset it" (:forgot-password-email-subject instance)))
      (is (= "invite@example.org" (:invitation-email-from instance)))
      (is (= "You're in" (:invitation-email-subject instance)))))

  (testing "absent, none of the four are present at all (optional keys, not nil)"
    (let [{:keys [instance]} (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                             "COOKIE_SECRET" "1234567890123456"})]
      (doseq [k [:jetty-host :media-cache-size-mb :forgot-password-email-from
                 :forgot-password-email-subject :invitation-email-from
                 :invitation-email-subject]]
        (is (not (contains? instance k)) (str k " should be absent, not nil"))))))

(deftest test-self-hosted-serves-real-http-on-the-port-from-env
  (testing "start-server? true really binds the port env-opts derived from PORT"
    (let [dir (fs/create-temp-dir {:prefix "sepal-main-http"})
          port (with-open [socket (ServerSocket. 0)]
                 (.getLocalPort socket))
          {:keys [process instance]} (main/env-opts {"SEPAL_DATA_HOME" (str dir)
                                                     "COOKIE_SECRET" "1234567890123456"
                                                     "APP_DOMAIN" "sepal.example.org"
                                                     "EXTENSIONS_LIBRARY_PATH" (System/getenv "EXTENSIONS_LIBRARY_PATH")
                                                     "PORT" (str port)})]
      (is (= port (:jetty-port instance)) "PORT must round-trip through parse-long")
      (instance/provision! {:db-path (:db-path instance)})
      (let [started (instance/start-process! process)]
        (try
          (let [garden (instance/start! started (assoc instance :start-server? true))]
            (try
              (let [response (http/get (str "http://127.0.0.1:" port "/ok"))]
                (is (= 204 (:status response))))
              (finally
                (instance/stop! garden))))
          (finally
            (instance/stop-process! started)
            (fs/delete-tree dir)))))))
