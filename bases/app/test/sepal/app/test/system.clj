(ns sepal.app.test.system
  (:require [integrant.core :as ig]
            [sepal.config.interface :as config.i]
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

(defn load-config [config]
  (config.i/read-config config {:profile :test}))

(defn default-system-config []
  (let [db-path (.getAbsolutePath (File/createTempFile "sepal-test" ".db"))
        schema-dump-file (or (System/getenv "SCHEMA_DUMP_FILE") "db/schema.sql")
        extension-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")]
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
                                                 :mail (ig/ref ::mock-mail-client)}
                               :cookie-secret "1234567890123456"
                               :start-server? false}
     :sepal.database.interface/schema {:database-path db-path
                                       :schema-dump-file schema-dump-file}
     :sepal.malli.interface/init {}}))

(def default-system-fixture
  (let [system-config (default-system-config)]
    (test.i/create-system-fixture system-config
                                  (fn [system f]
                                    (let [db (-> system :sepal.app.server/zodiac ::z.sql/db)]
                                      ;; Load schema for in-memory test database
                                      (binding [*system* system
                                                *db* db
                                                *app* (-> system :sepal.app.server/zodiac ::z/app)
                                                *cookie-store* (-> system :sepal.app.server/zodiac ::z/cookie-store)
                                                *mail-client* (-> system ::mock-mail-client)
                                                *token-service* (-> system ::token.i/service)]
                                        (f))))
                                  (keys system-config))))
