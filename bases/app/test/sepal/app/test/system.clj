(ns sepal.app.test.system
  (:require [babashka.fs :as fs]
            [integrant.core :as ig]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.mail.interface.protocols :as mail.p]
            [sepal.test.interface :as test.i]
            [sepal.token.interface :as token.i]
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

(defmethod ig/init-key ::mock-mail-client [_ _]
  (create-mock-mail-client))

(def ^:dynamic *app* nil)
(def ^:dynamic *db* nil)
(def ^:dynamic *system* nil)
(def ^:dynamic *cookie-store* nil)
(def ^:dynamic *mail-client* nil)
(def ^:dynamic *token-service* nil)
(def ^:dynamic *backup-dir* nil)

(defn default-system-config []
  (let [db-path (.getAbsolutePath (File/createTempFile "sepal-test" ".db"))
        extension-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")
        backup-dir (str (fs/create-temp-dir {:prefix "sepal-test-backups"}))]
    {:sepal.app.server/zodiac-sql {:database-path db-path
                                   :pragmas {:journal_mode "WAL"
                                             :foreign_keys "ON"
                                             :enable_load_extension "true"}
                                   :extensions ["mod_spatialite"]
                                   :extension-library-path extension-library-path
                                   :context-key :db}
     :sepal.app.server/zodiac-assets {:build? false
                                      :manifest-path "app/build/.vite/manifest.json"
                                      :asset-resource-path "app/build/assets"
                                      :package-json-dir "bases/app"}
     ::mock-mail-client {}
     ::token.i/service {:secret "test-secret-1234"}

     :sepal.app.server/zodiac {:extensions [(ig/ref :sepal.app.server/zodiac-sql)
                                            (ig/ref :sepal.app.server/zodiac-assets)]
                               :request-context {:forgot-password-email-from "support@sepal.app"
                                                 :forgot-password-email-subject "Sepal - Reset Password"
                                                 :invitation-email-from "noreply@sepal.app"
                                                 :invitation-email-subject "You've been invited to Sepal"
                                                 :token-service (ig/ref ::token.i/service)
                                                 :app-domain "test.sepal.app"
                                                 :mail (ig/ref ::mock-mail-client)
                                                 :backup-dir backup-dir
                                                 :media-key-prefix "media/"
                                                 :media-upload-bucket "sepal-test-media"}
                               :cookie-secret "1234567890123456"
                               :start-server? false}
     :sepal.database.interface/schema {:db-path db-path}
     :sepal.malli.interface/init {}}))

(def default-system-fixture
  (let [system-config (default-system-config)
        backup-dir (get-in system-config [:sepal.app.server/zodiac :request-context :backup-dir])]
    (test.i/create-system-fixture system-config
                                  (fn [system f]
                                    (let [db (-> system :sepal.app.server/zodiac ::z.sql/db)]
                                      ;; Mark setup as complete so tests bypass the setup wizard
                                      (setup.shared/complete-setup! db)
                                      (binding [*system* system
                                                *db* db
                                                *app* (-> system :sepal.app.server/zodiac ::z/app)
                                                *cookie-store* (-> system :sepal.app.server/zodiac ::z/cookie-store)
                                                *mail-client* (-> system ::mock-mail-client)
                                                *token-service* (-> system ::token.i/service)
                                                *backup-dir* backup-dir]
                                        (f))))
                                  (keys system-config))))
