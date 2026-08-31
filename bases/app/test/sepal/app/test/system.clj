(ns sepal.app.test.system
  "The test fixture, built from the same instance API production uses.

  Going through start-process! and start! rather than a hand-written integrant
  config is the point: a config maintained only for tests is a config that lets
  the suite stay green while the path a real caller takes is broken."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [sepal.app.instance :as instance]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.mail.interface.protocols :as mail.p]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql])
  (:import [java.io File]))

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

(defn load-floor-schema!
  "Build a database at minimum-schema-version from the snapshot in test
  resources.

  The floor leg cannot use provision!, which loads the *current* schema and so
  builds the same database as the latest leg. It cannot replay migrations from
  empty either: that runs InitSpatialMetaData and produces ~20 SpatiaLite
  bookkeeping tables no real garden has. So it loads a dump of the schema as it
  stood at the floor, which is what a garden at the floor actually looks like.

  When the floor moves, snapshot the schema as it stands at the new floor -- the
  error below names the file."
  [{:keys [db-path]}]
  (let [version (instance/minimum-schema-version)
        resource-name (str "test/schema-" version ".sql")
        resource (io/resource resource-name)]
    (when-not resource
      (throw (ex-info (str resource-name " is not on the test classpath. The floor moved; "
                           "snapshot schema.sql as it stands at the new floor into "
                           "components/test/resources/" resource-name ".")
                      {:reason :floor-snapshot-missing :version version})))
    (when-let [parent (fs/parent db-path)]
      (fs/create-dirs parent))
    (let [file (File/createTempFile "sepal-floor-schema" ".sql")]
      (try
        (with-open [in (io/input-stream resource)]
          (io/copy in file))
        (shell/sh "sqlite3" "-bail" "-init" (.getAbsolutePath file) db-path "")
        (finally
          (.delete file)))))
  {:db-path db-path})

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
    ;; The floor leg builds a database as it stood at the floor and lets
    ;; migrate! below carry it up to latest, which is the N-1 path. The latest
    ;; leg provisions from the current schema. 022 left an assertion here
    ;; instead, because while the floor was the latest there was no snapshot to
    ;; load; load-floor-schema! is what replaced it.
    (if (= "floor" (System/getenv "SEPAL_TEST_SCHEMA_VERSION"))
      (load-floor-schema! {:db-path db-path})
      (instance/provision! {:db-path db-path}))
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
