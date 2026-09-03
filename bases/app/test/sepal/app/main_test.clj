(ns sepal.app.main-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [hato.client :as http]
            [malli.core :as m]
            [sepal.app.instance :as instance]
            [sepal.app.main :as main]
            [sepal.config.interface :as config.i])
  (:import [java.net ServerSocket]))

(deftest test-env-opts-satisfy-the-instance-schemas
  (testing "the opts built from the environment are valid, so self-hosted boot
            cannot fail on a schema the library enforces"
    (let [{:keys [process instance]} (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                                     "SEPAL_SECRET" "1234567890123456"
                                                     "APP_DOMAIN" "sepal.example.org"})]
      (is (m/validate instance/ProcessOpts process)
          (str "invalid process opts: " (pr-str process)))
      (is (m/validate instance/InstanceOpts instance)
          (str "invalid instance opts: " (pr-str instance))))))

(deftest test-one-variable-install-satisfies-both-schemas
  (testing "SEPAL_SECRET alone is enough for a self-hosted install: every
            other opt is derived, so the next required opt could otherwise
            silently break the one-variable install"
    (let [{:keys [process instance]} (main/env-opts {"SEPAL_SECRET" "1234567890123456"})
          home (config.i/data-home {})]
      (is (m/validate instance/ProcessOpts process)
          (str "invalid process opts: " (pr-str process)))
      (is (m/validate instance/InstanceOpts instance)
          (str "invalid instance opts: " (pr-str instance)))
      (is (= (str (fs/path home "sepal.db")) (:db-path instance)))
      (is (= (str (fs/path home "cache")) (:media-cache-dir instance)))
      (is (= (str (fs/path home "backups")) (:backup-dir instance))))))

(deftest test-a-missing-sepal-secret-is-refused
  (testing "an unset SEPAL_SECRET fails loudly rather than defaulting to a
            master secret that is published in this repository"
    (let [thrown (try
                   (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"})
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (some? thrown) "env-opts should have thrown")
      (is (= :missing-sepal-secret (:reason (ex-data thrown))))
      (is (re-find #"SEPAL_SECRET" (ex-message thrown))
          "the message must name the variable the operator has to set")))

  (testing "an empty SEPAL_SECRET is the same as an unset one"
    (let [thrown (try
                   (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                   "SEPAL_SECRET" ""})
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (= :missing-sepal-secret (:reason (ex-data thrown)))))))

(deftest test-self-hosted-uses-one-fixed-slug-and-database
  (testing "a self-hosted install is one garden under SEPAL_DATA_HOME"
    (let [{:keys [instance]} (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                             "SEPAL_SECRET" "1234567890123456"})]
      (is (= "self-hosted" (:slug instance)))
      (is (= "/tmp/sepal-selfhosted/sepal.db" (:db-path instance)))
      (is (= "/tmp/sepal-selfhosted/backups" (:backup-dir instance)))
      (is (= "/tmp/sepal-selfhosted/cache" (:media-cache-dir instance))))))

(deftest test-env-opts-honours-host-cache-size-and-email-vars
  (testing "env vars system.edn used to read are not silently dropped by env-opts"
    (let [{:keys [instance]} (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-selfhosted"
                                             "SEPAL_SECRET" "1234567890123456"
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
                                             "SEPAL_SECRET" "1234567890123456"})]
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
                                                     "SEPAL_SECRET" "1234567890123456"
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

(deftest test-every-system-edn-variable-still-has-a-home
  (testing "each environment variable the deleted system.edn read reaches the
            opts. This is the audit that deletion demands, written down so it
            runs rather than being performed once by reading."
    (let [{:keys [process instance]}
          (main/env-opts {"SEPAL_DATA_HOME" "/tmp/sepal-audit"
                          "SEPAL_SECRET" "1234567890123456"
                          "LOG_LEVEL" "INFO"
                          "EXTENSIONS_LIBRARY_PATH" "/nix/store/spatialite/lib"
                          "APP_DOMAIN" "garden.example.org"
                          "PORT" "8080"
                          "HOST" "127.0.0.1"
                          "BACKUP_PATH" "/srv/backups"
                          "MEDIA_KEY_PREFIX" "garden/"
                          "MEDIA_UPLOAD_BUCKET" "garden-media"
                          "IMAGE_CACHE_SIZE_MB" "250"
                          "AWS_ACCESS_KEY_ID" "key-id"
                          "AWS_SECRET_ACCESS_KEY" "secret-key"
                          "AWS_REGION" "auto"
                          "AWS_S3_ENDPOINT" "https://s3.example.org"
                          "SMTP_HOST" "smtp.example.org"
                          "SMTP_PORT" "2525"
                          "SMTP_USERNAME" "postmaster"
                          "SMTP_PASSWORD" "hunter2"
                          "SMTP_AUTH" "false"
                          "SMTP_TLS" "tls"})]
      (testing "process scope"
        (is (= "INFO" (:log-level process)))
        (is (= "/nix/store/spatialite/lib" (:extensions-library-path process)))
        (is (= {:host "smtp.example.org"
                :port "2525"
                :username "postmaster"
                :password "hunter2"
                :auth "false"
                :tls "tls"}
               (:smtp process)))
        (is (= {:endpoint-override "https://s3.example.org"
                :region "auto"
                :access-key-id "key-id"
                :secret-access-key "secret-key"
                :media-upload-bucket "garden-media"}
               (:s3 process))))

      (testing "instance scope"
        (is (= "garden.example.org" (:app-domain instance)))
        (is (= 8080 (:jetty-port instance)))
        (is (= "127.0.0.1" (:jetty-host instance)))
        (is (= "/srv/backups" (:backup-dir instance)))
        (is (= "garden/" (:media-key-prefix instance)))
        (is (= 250 (:media-cache-size-mb instance)))
        (is (= "/tmp/sepal-audit/sepal.db" (:db-path instance)))
        (is (= "/tmp/sepal-audit/cache" (:media-cache-dir instance)))))))

(deftest test-app-url-scheme-is-configured-not-guessed
  ;; Every link a garden mails — invitations, password resets, backup downloads —
  ;; is built from this. It is a variable rather than something inferred from the
  ;; hostname: an install reached over plain HTTP is a deployment decision, and a
  ;; rule like "localhost means http" would be wrong for the first person who
  ;; puts a garden on a private hostname behind TLS, or on http elsewhere.
  (testing "unset, no base url is passed and the instance builds https itself"
    (let [{:keys [instance]} (main/env-opts {"SEPAL_SECRET" "1234567890123456"
                                             "APP_DOMAIN" "garden.example.org"})]
      (is (nil? (:app-base-url instance)))))

  (testing "set, it carries the scheme with whatever APP_DOMAIN holds"
    ;; APP_DOMAIN may include a port here, unlike under the dispatcher, because
    ;; nothing routes on it in a self-hosted install.
    (let [{:keys [instance]} (main/env-opts {"SEPAL_SECRET" "1234567890123456"
                                             "APP_DOMAIN" "localhost:8000"
                                             "APP_URL_SCHEME" "http"})]
      (is (= "http://localhost:8000" (:app-base-url instance))))))

(deftest test-smtp-debug-is-opt-in
  (testing "SMTP_DEBUG reaches the smtp opts, so the conversation can be logged
            from a self-hosted start rather than only from a caller that builds
            opts by hand"
    (let [{:keys [process]} (main/env-opts {"SEPAL_SECRET" "1234567890123456"
                                            "SMTP_HOST" "smtp.example.org"
                                            "SMTP_DEBUG" "true"})]
      (is (= "true" (:debug (:smtp process))))
      (is (m/validate instance/ProcessOpts process)
          (str "invalid process opts: " (pr-str process)))))

  (testing "without SMTP_DEBUG the key is absent, so the default stays off and
            the opts map does not grow a nil"
    (let [{:keys [process]} (main/env-opts {"SEPAL_SECRET" "1234567890123456"
                                            "SMTP_HOST" "smtp.example.org"})]
      (is (not (contains? (:smtp process) :debug))))))

(deftest test-token-secret-is-derived-not-read
  (testing "system.edn read TOKEN_SECRET; secrets are derived from SEPAL_SECRET
            now, so the variable must not quietly come back"
    (let [{:keys [process instance]}
          (main/env-opts {"SEPAL_SECRET" "1234567890123456"
                          "TOKEN_SECRET" "should-be-ignored"})]
      (is (not-any? #(= "should-be-ignored" %) (vals process)))
      (is (not-any? #(= "should-be-ignored" %) (vals instance))))))

(deftest test-the-synonym-ref-path-comes-from-the-environment
  (testing "an explicit path wins"
    (is (= "/srv/syn.db"
           (-> (main/env-opts {"SEPAL_SECRET" "0123456789abcdef"
                               "WFO_SYNONYM_REF_PATH" "/srv/syn.db"})
               :process :wfo-synonym-ref-path))))

  ;; Hermetic: SEPAL_DATA_HOME is pinned to a temp dir rather than left to
  ;; resolve the real platform default, so nothing here depends on what happens
  ;; to be on the machine.
  ;;
  ;; This used to resolve the default only when the file already existed. It
  ;; does not any more: the setup wizard downloads the reference to this path,
  ;; so on a first run -- when by definition no file is there -- the wizard has
  ;; to be told where to put it. The degrade is unchanged, because
  ;; sepal.synonym.reference/open still returns nil for a path with no file, so
  ;; a resolved-but-absent path means no pool and an empty WFO half exactly as
  ;; a nil path did.
  (let [dir (fs/create-temp-dir {:prefix "sepal-wfo-ref-path"})
        default (str (fs/path dir "sepal-synonyms.db"))]
    (try
      (testing "a blank assignment is not a path: it falls back to the default"
        ;; "" is truthy in Clojure, so this is the case a plain `or` gets wrong.
        ;; The point of the assertion is that the result is the default and not
        ;; the empty string.
        (is (= default
               (-> (main/env-opts {"SEPAL_SECRET" "0123456789abcdef"
                                   "SEPAL_DATA_HOME" (str dir)
                                   "WFO_SYNONYM_REF_PATH" ""})
                   :process :wfo-synonym-ref-path))))

      (testing "the default is resolved before the file exists, because that is
                where the setup wizard downloads it to"
        (is (not (fs/exists? default)))
        (is (= default
               (-> (main/env-opts {"SEPAL_SECRET" "0123456789abcdef"
                                   "SEPAL_DATA_HOME" (str dir)})
                   :process :wfo-synonym-ref-path))))

      (testing "and an explicit path still wins over the default, whether or not
                a file is at either one"
        (spit default "")
        (is (= "/srv/syn.db"
               (-> (main/env-opts {"SEPAL_SECRET" "0123456789abcdef"
                                   "SEPAL_DATA_HOME" (str dir)
                                   "WFO_SYNONYM_REF_PATH" "/srv/syn.db"})
                   :process :wfo-synonym-ref-path))))
      (finally (fs/delete-tree dir)))))
