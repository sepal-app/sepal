(ns sepal.app.main
  "The self-hosted entry point: read the environment, start one instance, serve.

  This is the only namespace in Sepal that reads environment variables. The
  library takes opts."
  (:require [babashka.fs :as fs]
            [sepal.app.instance :as instance]
            [sepal.config.interface :as config.i])
  (:gen-class))

(def ^:private self-hosted-slug "self-hosted")

(defn- master-secret
  "SEPAL_SECRET, with no default. The session cookie key and the token secret
  are both derived from it, so any default published here would be one session
  key and one password-reset secret shared by every install that forgot to set
  it. The length rule lives in sepal.app.instance/ProcessOpts, which rejects a
  short value at start-process!."
  [env]
  (or (not-empty (get env "SEPAL_SECRET"))
      (throw (ex-info (str "SEPAL_SECRET is not set. Sepal derives the session cookie key "
                           "and the password reset token secret from it, so it has no default. "
                           "Set it to at least 16 random characters, for example "
                           "SEPAL_SECRET=$(openssl rand -hex 16). "
                           "This was called COOKIE_SECRET before it came to cover both secrets.")
                      {:reason :missing-sepal-secret}))))

(defn env-opts
  "Build process and instance opts from an environment map. Throws when
  SEPAL_SECRET is missing."
  [env]
  (let [home (config.i/data-home env)
        jetty-port (some-> (get env "PORT") parse-long)
        jetty-host (get env "HOST")
        media-cache-size-mb (some-> (get env "IMAGE_CACHE_SIZE_MB") parse-long)
        forgot-password-email-from (get env "FORGOT_PASSWORD_EMAIL_FROM")
        forgot-password-email-subject (get env "FORGOT_PASSWORD_EMAIL_SUBJECT")
        invitation-email-from (get env "INVITATION_EMAIL_FROM")
        invitation-email-subject (get env "INVITATION_EMAIL_SUBJECT")
        ;; Configured, never inferred. An install reached over plain HTTP is a
        ;; deployment decision, and a rule like "localhost means http" would be
        ;; wrong for a garden on a private hostname behind TLS, and for one on
        ;; http anywhere else. Unset, no :app-base-url is passed and start!
        ;; builds https on :app-domain, which is what a served garden is.
        app-url-scheme (get env "APP_URL_SCHEME")
        app-domain (get env "APP_DOMAIN" "localhost")]
    {:process (cond-> {:master-secret (master-secret env)
                       :log-level (get env "LOG_LEVEL" "DEBUG")
                       :extensions-library-path (get env "EXTENSIONS_LIBRARY_PATH")
                       :wfo-synonym-ref-path
                       (or (not-empty (get env "WFO_SYNONYM_REF_PATH"))
                           (let [default (str (fs/path home "sepal-synonyms.db"))]
                             (when (fs/exists? default) default)))}
                (get env "SMTP_HOST")
                (assoc :smtp (cond-> {:host (get env "SMTP_HOST")
                                      :port (get env "SMTP_PORT" "587")
                                      :username (get env "SMTP_USERNAME")
                                      :password (get env "SMTP_PASSWORD")
                                      :auth (get env "SMTP_AUTH" "true")
                                      :tls (get env "SMTP_TLS" "starttls")}
                               ;; Added only when asked for. The key stays absent
                               ;; otherwise, because the log prints addresses and
                               ;; every server reply.
                               (get env "SMTP_DEBUG")
                               (assoc :debug (get env "SMTP_DEBUG"))))

                (get env "AWS_ACCESS_KEY_ID")
                (assoc :s3 {:endpoint-override (get env "AWS_S3_ENDPOINT")
                            :region (get env "AWS_REGION")
                            :access-key-id (get env "AWS_ACCESS_KEY_ID")
                            :secret-access-key (get env "AWS_SECRET_ACCESS_KEY")
                            :media-upload-bucket (get env "MEDIA_UPLOAD_BUCKET" "media")}))
     :instance (cond-> {:slug self-hosted-slug
                        :db-path (str (fs/path home "sepal.db"))
                        :app-domain app-domain
                        ;; Not normalized: an operator's slashless value must fail
                        ;; validation at start! rather than silently be corrected to
                        ;; point somewhere an existing install never wrote.
                        :media-key-prefix (get env "MEDIA_KEY_PREFIX" "media/")
                        :media-cache-dir (str (fs/path home "cache"))
                        :backup-dir (or (get env "BACKUP_PATH") (str (fs/path home "backups")))}
                 jetty-port (assoc :jetty-port jetty-port)
                 jetty-host (assoc :jetty-host jetty-host)
                 media-cache-size-mb (assoc :media-cache-size-mb media-cache-size-mb)
                 forgot-password-email-from (assoc :forgot-password-email-from forgot-password-email-from)
                 forgot-password-email-subject (assoc :forgot-password-email-subject forgot-password-email-subject)
                 invitation-email-from (assoc :invitation-email-from invitation-email-from)
                 invitation-email-subject (assoc :invitation-email-subject invitation-email-subject)
                 ;; APP_DOMAIN may carry a port here, unlike under the dispatcher,
                 ;; because nothing routes on it in a self-hosted install.
                 app-url-scheme (assoc :app-base-url (str app-url-scheme "://" app-domain)))}))

(defn -main [& _]
  (try
    (let [{:keys [process instance]} (env-opts (System/getenv))
          started (instance/start-process! process)]
      (when-not (fs/exists? (:db-path instance))
        (instance/provision! {:db-path (:db-path instance)}))
      (let [pending (instance/schema-version {:db-path (:db-path instance)})]
        (when (not= pending (instance/latest-schema-version))
          (println "Applying pending migrations...")
          (instance/migrate! {:db-path (:db-path instance)})))
      (instance/start! started (assoc instance :start-server? true))
      @(promise))
    (catch Exception exc
      (println (ex-message exc))
      (if-let [data (ex-data exc)]
        (println data)
        (println exc))
      (System/exit 1))))
