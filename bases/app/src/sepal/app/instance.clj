(ns sepal.app.instance
  "Run Sepal as a library: one process, many instances.

  `start-process!` owns what cannot be per instance — logging, the malli registry
  and the shared collaborators. `start!` owns one garden: its connection pool, its
  compiled router, its cookie key and its token secret. Nothing here knows what a
  tenant is; the caller supplies a slug and a database path."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql])
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(set! *warn-on-reflection* true)

(def ^:private hmac-algorithm "HmacSHA256")

(defn- hmac-sha256
  ^bytes [^String secret ^String message]
  (let [mac (Mac/getInstance hmac-algorithm)]
    (.init mac (SecretKeySpec. (.getBytes secret "UTF-8") hmac-algorithm))
    (.doFinal mac (.getBytes message "UTF-8"))))

(defn- ->hex [^bytes bs]
  (str/join (map #(format "%02x" (bit-and % 0xff)) bs)))

(defn- derive-secret
  "HMAC-SHA256(master-secret, slug/purpose). A stand-in for HKDF with the same
  call shape and no key-stretching claim."
  ^bytes [master-secret slug purpose]
  (hmac-sha256 master-secret (str slug "/" purpose)))

(defn- cookie-key
  "16 raw bytes, which is what zodiac's :cookie-secret takes."
  ^bytes [master-secret slug]
  (byte-array (take 16 (derive-secret master-secret slug "cookie"))))

(defn- token-secret
  "64 hex characters. sepal.token requires a string of at least 16."
  [master-secret slug]
  (->hex (derive-secret master-secret slug "token")))

(defn- table-exists?
  [db-path table]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})
        row (jdbc/execute-one! ds
                               ["select count(*) from sqlite_master
                                 where type = 'table' and name = ?" table])]
    (pos? (or (first (vals row)) 0))))

(defn provision!
  "Create a database at :db-path and load :schema-file into it. Refuses to touch
  an existing file. Returns {:db-path ...}."
  [{:keys [db-path schema-file]}]
  (when (fs/exists? db-path)
    (throw (ex-info (format "A database already exists at %s" db-path)
                    {:reason :database-exists :db-path db-path})))
  (when-not (fs/exists? schema-file)
    (throw (ex-info (format "No schema file at %s" schema-file)
                    {:reason :schema-file-missing :schema-file schema-file})))
  (when-let [parent (fs/parent db-path)]
    (fs/create-dirs parent))
  (let [{:keys [err]} (db.i/load-schema! {:database-path db-path
                                          :schema-dump-file schema-file})]
    ;; sqlite3 -init reports errors on stderr and still exits 0, so check the
    ;; result rather than the exit code.
    (when-not (table-exists? db-path "user")
      (throw (ex-info (format "The schema did not load into %s" db-path)
                      {:reason :schema-load-failed
                       :db-path db-path
                       :schema-file schema-file
                       :stderr err}))))
  {:db-path db-path})

(defn- process-config
  [{:keys [log-level media-cache-dir media-cache-size-mb smtp s3]}]
  (cond-> {:sepal.logging.interface/logging {:level log-level}
           :sepal.malli.interface/init {}}

    media-cache-dir
    (assoc :sepal.media-transform.interface/service
           {:cache-dir media-cache-dir
            :max-cache-size-mb (or media-cache-size-mb 500)})

    smtp
    (assoc :sepal.mail.interface/client smtp)

    s3
    (assoc :sepal.aws-s3.interface/credentials-provider
           {:access-key-id (:access-key-id s3)
            :secret-access-key (:secret-access-key s3)}

           :sepal.aws-s3.interface/s3-client
           {:endpoint-override (:endpoint-override s3)
            :credentials-provider (ig/ref :sepal.aws-s3.interface/credentials-provider)}

           :sepal.aws-s3.interface/s3-presigner
           {:endpoint-override (:endpoint-override s3)
            :credentials-provider (ig/ref :sepal.aws-s3.interface/credentials-provider)})))

(defn start-process!
  "Initialize what cannot be per instance and build the shared collaborators.
  Returns an opaque process value to pass to start!. :smtp and :s3 may be
  omitted, in which case mail and media are unavailable to every instance."
  [{:keys [master-secret extensions-library-path media-cache-dir] :as opts}]
  (when (str/blank? master-secret)
    (throw (ex-info "A master-secret is required" {:reason :missing-master-secret})))
  (when media-cache-dir
    (fs/create-dirs media-cache-dir))
  (let [config (process-config opts)
        _ (ig/load-namespaces config)
        system (ig/init config)]
    {:system system
     :master-secret master-secret
     :extensions-library-path extensions-library-path
     :media-upload-bucket (get-in opts [:s3 :media-upload-bucket])
     :mail (:sepal.mail.interface/client system)
     :s3-client (:sepal.aws-s3.interface/s3-client system)
     :s3-presigner (:sepal.aws-s3.interface/s3-presigner system)
     :media-transform-service (:sepal.media-transform.interface/service system)}))

(defn stop-process! [process]
  (ig/halt! (:system process))
  nil)

(def ^:private sqlite-pragmas
  {:journal_mode "WAL"
   :foreign_keys "ON"
   :enable_load_extension "true"})

(defn- instance-config
  [process {:keys [slug db-path app-domain media-key-prefix]}]
  {:sepal.token.interface/service
   {:secret (token-secret (:master-secret process) slug)}

   :sepal.app.server/zodiac-sql
   ;; No :schema-dump-file: it drives a dead guard in server.clj that would
   ;; re-run load-schema! on every start. provision! owns schema loading.
   {:database-path db-path
    :pragmas sqlite-pragmas
    :extensions ["mod_spatialite"]
    :extension-library-path (:extensions-library-path process)
    :context-key :db}

   :sepal.app.server/zodiac-assets
   ;; :vite nil, not omitted — an omitted :vite means {:mode :build}, which runs
   ;; npm and vite once per instance.
   {:manifest-path "app/build/.vite/manifest.json"
    :asset-resource-path "app/build/assets"
    :cache-manifest? true
    :vite nil}

   :sepal.app.server/zodiac
   {:extensions [(ig/ref :sepal.app.server/zodiac-sql)
                 (ig/ref :sepal.app.server/zodiac-assets)]
    :cookie-secret (cookie-key (:master-secret process) slug)
    :start-server? false
    :request-context {:app-domain app-domain
                      :mail (:mail process)
                      :token-service (ig/ref :sepal.token.interface/service)
                      :s3-client (:s3-client process)
                      :s3-presigner (:s3-presigner process)
                      :media-transform-service (:media-transform-service process)
                      :media-upload-bucket (:media-upload-bucket process)
                      :media-key-prefix (or media-key-prefix (str slug "/"))
                      :forgot-password-email-from "support@sepal.app"
                      :forgot-password-email-subject "Sepal - Reset Password"
                      :invitation-email-from "noreply@sepal.app"
                      :invitation-email-subject "You've been invited to Sepal"}}})

(defn start!
  "Start one garden. Returns an opaque instance value."
  [process {:keys [slug db-path] :as opts}]
  (when-not (fs/exists? db-path)
    (throw (ex-info (format "No database at %s" db-path)
                    {:reason :database-missing :slug slug :db-path db-path})))
  (let [config (instance-config process opts)]
    (ig/load-namespaces config)
    {:slug slug
     :db-path db-path
     :system (ig/init config)}))

(defn handler
  "The ring handler for an instance."
  [instance]
  (get-in instance [:system :sepal.app.server/zodiac ::z/app]))

(defn stop! [instance]
  (ig/halt! (:system instance))
  nil)

(defn- instance-db
  [instance]
  (get-in instance [:system :sepal.app.server/zodiac ::z.sql/db]))

(defn create-admin-user!
  "Create an active admin user in a running instance and mark its setup wizard
  complete. Takes the instance rather than a path so the caller never holds a
  database handle."
  [instance {:keys [email password]}]
  (let [db (instance-db instance)]
    (when (user.i/exists? db email)
      (throw (ex-info (format "A user already exists for %s" email)
                      {:reason :user-exists :email email :slug (:slug instance)})))
    (let [user (user.i/create! db {:email email
                                   :password password
                                   :role :admin
                                   :status :active})]
      (when (error.i/error? user)
        (throw (ex-info "Could not create the admin user"
                        {:reason :create-user-failed :error user :email email})))
      (setup.shared/complete-setup! db)
      {:user-id (:user/id user)})))
