(ns sepal.app.test.system
  "The test fixture, built from the same instance API production uses.

  Going through start-process! and start! rather than a hand-written integrant
  config is the point: a config maintained only for tests is a config that lets
  the suite stay green while the path a real caller takes is broken."
  (:require [babashka.fs :as fs]
            [sepal.app.instance :as instance]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.mail.interface.protocols :as mail.p]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql]))

;; Mock mail client that records sent messages for testing
(defrecord MockMailClient [sent-messages]
  mail.p/MailClient
  (send-message [_ message]
    (swap! sent-messages conj message)
    {:status :sent}))

(defn create-mock-mail-client []
  (->MockMailClient (atom [])))

(def ^:dynamic *app* nil)
(def ^:dynamic *db* nil)
(def ^:dynamic *system* nil)
(def ^:dynamic *cookie-store* nil)
(def ^:dynamic *mail-client* nil)
(def ^:dynamic *token-service* nil)
(def ^:dynamic *backup-dir* nil)

(defn- start-test-instance []
  (let [dir (fs/create-temp-dir {:prefix "sepal-test"})
        db-path (str (fs/path dir "sepal.db"))
        backup-dir (str (fs/path dir "backups"))
        mail (create-mock-mail-client)
        process (instance/start-process!
                  {:master-secret "1234567890123456"
                   :mail mail
                   :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})]
    ;; start! creates the media cache directory but not the backup directory —
    ;; in production sepal.app.backup.core/ensure-backup-dir! makes it on first
    ;; use. Tests are handed *backup-dir* to write into directly, so make it
    ;; usable here.
    (fs/create-dirs backup-dir)
    (instance/provision! {:db-path db-path})
    (when (not= (instance/schema-version {:db-path db-path})
                (instance/latest-schema-version))
      (instance/migrate! {:db-path db-path}))
    (let [garden (instance/start! process
                                  {:slug "test"
                                   :db-path db-path
                                   :app-domain "test.sepal.app"
                                   :media-key-prefix "media/"
                                   :media-cache-dir (str (fs/path dir "cache"))
                                   :backup-dir backup-dir
                                   :start-server? false})]
      {:dir dir
       :process process
       :garden garden
       :mail mail
       :backup-dir backup-dir})))

(defn default-system-fixture [f]
  (let [{:keys [dir process garden mail backup-dir]} (start-test-instance)
        db (get-in garden [:system :sepal.app.server/zodiac ::z.sql/db])]
    (try
      ;; Mark setup as complete so tests bypass the setup wizard
      (setup.shared/complete-setup! db)
      (binding [*system* (:system garden)
                *db* db
                *app* (instance/handler garden)
                *cookie-store* (get-in garden [:system :sepal.app.server/zodiac ::z/cookie-store])
                *mail-client* mail
                *token-service* (get-in garden [:system :sepal.token.interface/service])
                *backup-dir* backup-dir]
        (f))
      (finally
        (instance/stop! garden)
        (instance/stop-process! process)
        (fs/delete-tree dir)))))
