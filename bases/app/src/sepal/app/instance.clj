(ns sepal.app.instance
  "Run Sepal as a library: one process, many instances.

  `start-process!` owns what cannot be per instance — logging, the malli registry
  and the shared collaborators. `start!` owns one garden: its connection pool, its
  compiled router, its cookie key and its token secret. Nothing here knows what a
  tenant is; the caller supplies a slug and a database path."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.error :as me]
            [next.jdbc :as jdbc]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql])
  (:import [javax.crypto KDF]
           [javax.crypto.spec HKDFParameterSpec]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; Opts

(def ^:private Slug
  ;; Lowercase, no dots or slashes: the slug is an HKDF salt, an S3 key prefix
  ;; and a hostname label, so it must be safe in all three.
  [:re #"^[a-z0-9][a-z0-9-]{0,62}$"])

(def ProcessOpts
  [:map {:closed true}
   [:master-secret [:string {:min 16}]]
   [:log-level {:optional true} [:maybe :string]]
   [:extensions-library-path {:optional true} [:maybe :string]]
   [:smtp {:optional true}
    [:maybe [:map {:closed true}
             [:host :string]
             [:port {:optional true} [:or :string :int]]
             [:username {:optional true} [:maybe :string]]
             [:password {:optional true} [:maybe :string]]
             [:auth {:optional true} [:or :string :boolean]]
             [:tls {:optional true} [:maybe :string]]]]]
   [:s3 {:optional true}
    [:maybe [:map {:closed true}
             [:endpoint-override {:optional true} [:maybe :string]]
             [:access-key-id :string]
             [:secret-access-key :string]
             [:media-upload-bucket :string]]]]])

(def InstanceOpts
  [:map {:closed true}
   [:slug Slug]
   [:db-path [:string {:min 1}]]
   [:app-domain [:string {:min 1}]]
   ;; Required, not derived: every tenant-bearing path is the caller's explicit
   ;; decision, so a missing one fails here instead of colliding at runtime.
   [:media-key-prefix [:string {:min 1}]]
   [:media-cache-dir [:string {:min 1}]]
   [:backup-dir [:string {:min 1}]]
   [:media-cache-size-mb {:optional true} pos-int?]])

(defn- validate!
  [schema opts what]
  (when-not (m/validate schema opts)
    (throw (ex-info (format "Invalid %s" what)
                    {:reason :invalid-opts
                     :errors (me/humanize (m/explain schema opts))})))
  nil)

;; -----------------------------------------------------------------------------
;; Secrets

(defn- ->hex [^bytes bs]
  (str/join (map #(format "%02x" (bit-and % 0xff)) bs)))

(defn- hkdf
  "HKDF-SHA256 per RFC 5869. Requires JDK 25 or later for javax.crypto.KDF."
  ^bytes [^bytes ikm ^bytes salt ^bytes info length]
  (let [kdf (KDF/getInstance "HKDF-SHA256")
        spec (-> (HKDFParameterSpec/ofExtract)
                 (.addIKM ikm)
                 (.addSalt salt)
                 (.thenExpand info length))]
    (.deriveData kdf spec)))

(defn- derive-secret
  "Per-instance key material. The slug is the salt, so two instances provably
  get different keys; the purpose is the info, so one instance's cookie key and
  token secret are independent."
  ^bytes [^String master-secret ^String slug ^String purpose length]
  (hkdf (.getBytes master-secret "UTF-8")
        (.getBytes slug "UTF-8")
        (.getBytes purpose "UTF-8")
        length))

(defn- cookie-key
  "16 raw bytes, which is what zodiac's :cookie-secret takes."
  ^bytes [master-secret slug]
  (derive-secret master-secret slug "cookie" 16))

(defn- token-secret
  "64 hex characters. sepal.token requires a string of at least 16."
  [master-secret slug]
  (->hex (derive-secret master-secret slug "token" 32)))

(defn- table-exists?
  [db-path table]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})
        row (jdbc/execute-one! ds
                               ["select count(*) from sqlite_master
                                 where type = 'table' and name = ?" table])]
    (pos? (or (first (vals row)) 0))))

(defn provision!
  "Create a database at :db-path and load the current schema into it. Refuses to
  touch an existing file. Returns {:db-path ...}."
  [{:keys [db-path]}]
  (when (fs/exists? db-path)
    (throw (ex-info (format "A database already exists at %s" db-path)
                    {:reason :database-exists :db-path db-path})))
  (when-let [parent (fs/parent db-path)]
    (fs/create-dirs parent))
  (let [{:keys [err]} (db.i/load-schema! {:db-path db-path})]
    ;; sqlite3 -init reports errors on stderr and still exits 0, so check the
    ;; result rather than the exit code.
    (when-not (table-exists? db-path "user")
      (throw (ex-info (format "The schema did not load into %s" db-path)
                      {:reason :schema-load-failed
                       :db-path db-path
                       :stderr err}))))
  {:db-path db-path})

;; Schema versioning, exposed on the instance API so the control plane never
;; needs to require a component namespace. Thin wrappers over sepal.database.

(defn schema-version
  "The migration version a database is at, or nil."
  [{:keys [db-path]}]
  (db.i/schema-version {:db-path db-path}))

(defn latest-schema-version
  "The migration version this build of Sepal expects."
  []
  (db.i/latest-version))

(defn migrate!
  "Apply pending migrations to a database, one transaction each."
  [{:keys [db-path]}]
  (db.i/migrate! {:db-path db-path}))

(defn preflight!
  "Migrate a VACUUM INTO snapshot and report, leaving the live database alone."
  [{:keys [db-path]}]
  (db.i/preflight! {:db-path db-path}))

(defn- process-config
  [{:keys [log-level smtp s3]}]
  (cond-> {:sepal.logging.interface/logging {:level log-level}
           :sepal.malli.interface/init {}}

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
  omitted, in which case mail and media uploads are unavailable to every
  instance."
  [{:keys [master-secret extensions-library-path] :as opts}]
  (validate! ProcessOpts opts "process opts")
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
     ;; The slugs and databases already running in this process. Two instances
     ;; on one database silently merge two gardens; two on one slug silently
     ;; share a cookie key and token secret. Neither is detectable after the
     ;; fact, so both are refused at start!.
     :registry (atom {:slugs #{} :db-paths #{}})}))

(defn stop-process! [process]
  (ig/halt! (:system process))
  nil)

(def ^:private sqlite-pragmas
  {:journal_mode "WAL"
   :foreign_keys "ON"
   :enable_load_extension "true"})

(defn- instance-config
  [process {:keys [slug db-path app-domain media-key-prefix media-cache-dir media-cache-size-mb backup-dir]}]
  {:sepal.token.interface/service
   {:secret (token-secret (:master-secret process) slug)}

   :sepal.media-transform.interface/service
   ;; Per instance, not shared: cache-key is SHA-256 over a per-database row id,
   ;; so two gardens sharing one cache directory would serve each other's
   ;; derivatives for the same id. Separate directories remove the collision.
   {:cache-dir media-cache-dir
    :max-cache-size-mb (or media-cache-size-mb 500)}

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
                      :media-transform-service (ig/ref :sepal.media-transform.interface/service)
                      :media-upload-bucket (:media-upload-bucket process)
                      :media-key-prefix media-key-prefix
                      :backup-dir backup-dir
                      :forgot-password-email-from "support@sepal.app"
                      :forgot-password-email-subject "Sepal - Reset Password"
                      :invitation-email-from "noreply@sepal.app"
                      :invitation-email-subject "You've been invited to Sepal"}}

   :sepal.scheduler.interface/scheduler {}

   :sepal.app.backup/job
   {:scheduler (ig/ref :sepal.scheduler.interface/scheduler)
    :zodiac (ig/ref :sepal.app.server/zodiac)
    :mail (:mail process)
    :app-domain app-domain
    :backup-dir backup-dir}})

(defn- claim!
  "Record a slug and database as in use by this process, or throw. Held under a
  lock rather than inside swap! because the check and the write must not be
  retried independently. Returns the canonical db path."
  [process slug db-path]
  (let [canonical (str (fs/canonicalize db-path {:nofollow-links true}))
        registry (:registry process)]
    (locking registry
      (let [{:keys [slugs db-paths]} @registry]
        (when (contains? slugs slug)
          (throw (ex-info (format "Slug %s is already running in this process" slug)
                          {:reason :duplicate-slug :slug slug})))
        (when (contains? db-paths canonical)
          (throw (ex-info (format "Database %s is already open in this process" canonical)
                          {:reason :duplicate-database :slug slug :db-path canonical})))
        (swap! registry #(-> % (update :slugs conj slug)
                             (update :db-paths conj canonical)))))
    canonical))

(defn- release!
  [process slug canonical-db-path]
  (swap! (:registry process)
         #(-> % (update :slugs disj slug)
              (update :db-paths disj canonical-db-path))))

(defn- probe!
  "Force a real connection so a corrupt database or a failed load_extension
  fails here rather than on a customer's first request. The pool is lazy: the
  datasource exists after ig/init but connects on first use."
  [system]
  (let [db (get-in system [:sepal.app.server/zodiac ::z.sql/db])]
    (jdbc/execute-one! db ["select 1"])))

(defn start!
  "Start one garden. Returns an opaque instance value."
  [process {:keys [slug db-path media-cache-dir] :as opts}]
  (validate! InstanceOpts opts "instance opts")
  (when-not (fs/exists? db-path)
    (throw (ex-info (format "No database at %s" db-path)
                    {:reason :database-missing :slug slug :db-path db-path})))
  (let [current (try
                  (db.i/schema-version {:db-path db-path})
                  (catch Exception e
                    ;; A file we cannot even read the version from is unusable —
                    ;; corrupt, or not a SQLite database at all.
                    (throw (ex-info (format "Database %s could not be read" db-path)
                                    {:reason :database-unusable :slug slug :db-path db-path}
                                    e))))
        expected (db.i/latest-version)]
    (when-not (= current expected)
      (throw (ex-info (format "Database %s is at schema version %s, code expects %s"
                              db-path current expected)
                      {:reason :schema-version-behind
                       :slug slug :db-path db-path
                       :current current :expected expected}))))
  (let [canonical (claim! process slug db-path)]
    (try
      (fs/create-dirs media-cache-dir)
      (let [config (instance-config process opts)
            _ (ig/load-namespaces config)
            system (try
                     (ig/init config)
                     (catch clojure.lang.ExceptionInfo e
                       ;; A later key (e.g. the backup job, which queries the
                       ;; database) can throw after earlier keys already built
                       ;; a connection pool. ig/init does not halt what it
                       ;; built, so this must, or the pool leaks.
                       (when-let [partial-system (:system (ex-data e))]
                         (ig/halt! partial-system))
                       (throw e)))]
        (try
          (probe! system)
          {:slug slug
           :db-path db-path
           :canonical-db-path canonical
           :process process
           :system system}
          (catch Throwable e
            (ig/halt! system)
            (throw (ex-info (format "Database %s could not be opened" db-path)
                            {:reason :database-unusable :slug slug :db-path db-path}
                            e)))))
      (catch Throwable e
        (release! process slug canonical)
        (throw e)))))

(defn handler
  "The ring handler for an instance."
  [instance]
  (get-in instance [:system :sepal.app.server/zodiac ::z/app]))

(defn stop! [instance]
  (let [{:keys [process slug canonical-db-path]} instance]
    (ig/halt! (:system instance))
    (when process
      (release! process slug canonical-db-path)))
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
