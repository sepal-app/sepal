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
            [pogonos.core :as mustache]
            [sepal.accession.interface :as accession.i]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.mail.interface :as mail.i]
            [sepal.mail.interface.protocols :as mail.p]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.token.interface :as token.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]
            [zodiac.ext.sql :as z.sql])
  (:import [java.sql SQLException]
           [javax.crypto KDF]
           [javax.crypto.spec HKDFParameterSpec]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; Opts

(def ^:private Slug
  ;; Lowercase, no dots or slashes: the slug is an HKDF salt, an S3 key prefix
  ;; and a hostname label, so it must be safe in all three.
  [:re #"^[a-z0-9][a-z0-9-]{0,62}$"])

(def ^:private MediaKeyPrefix
  ;; Must end in a slash: sepal.app.routes.media.keys/own-key? tells instances
  ;; apart with str/starts-with? on the raw key, so a slashless prefix like
  ;; "brooklyn" would also accept a sibling instance's "brooklynheights/...".
  [:re #"^.+/$"])

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
             [:tls {:optional true} [:maybe :string]]
             ;; Logs the SMTP conversation. Off unless asked for: it prints
             ;; addresses and every server reply.
             [:debug {:optional true} [:or :string :boolean]]]]]
   [:s3 {:optional true}
    [:maybe [:map {:closed true}
             [:endpoint-override {:optional true} [:maybe :string]]
             [:access-key-id :string]
             [:secret-access-key :string]
             [:media-upload-bucket :string]]]]
   ;; A caller may bring its own client rather than have one built from :smtp.
   ;; When present, :smtp is not used and no client is built.
   [:mail {:optional true} [:fn #(satisfies? mail.p/MailClient %)]]])

(def InstanceOpts
  [:map {:closed true}
   [:slug Slug]
   [:db-path [:string {:min 1}]]
   [:app-domain [:string {:min 1}]]
   ;; Required, not derived: every tenant-bearing path is the caller's explicit
   ;; decision, so a missing one fails here instead of colliding at runtime.
   [:media-key-prefix MediaKeyPrefix]
   [:media-cache-dir [:string {:min 1}]]
   [:backup-dir [:string {:min 1}]]
   [:media-cache-size-mb {:optional true} pos-int?]
   [:start-server? {:optional true} :boolean]
   [:jetty-host {:optional true} [:maybe :string]]
   [:jetty-port {:optional true} pos-int?]
   ;; Development knobs, passed through to zodiac untouched. Their defaults suit
   ;; production and the REPL is the only caller that sets them.
   ;; :reload-per-request? recompiles on every request and must never be set by
   ;; the control plane. Omitting :vite is not the same as passing nil: see the
   ;; note on ::zodiac-assets in instance-config.
   [:vite {:optional true} [:maybe :map]]
   [:hot-reload {:optional true} [:maybe :map]]
   [:reload-per-request? {:optional true} :boolean]
   [:forgot-password-email-from {:optional true} [:string {:min 1}]]
   [:forgot-password-email-subject {:optional true} [:string {:min 1}]]
   [:invitation-email-from {:optional true} [:string {:min 1}]]
   [:invitation-email-subject {:optional true} [:string {:min 1}]]])

(def Usage
  "The countable things in a garden. Closed, so adding one is a deliberate change
  to this published API rather than a silent widening under its consumers."
  [:map {:closed true}
   [:accessions [:int {:min 0}]]
   [:materials [:int {:min 0}]]
   [:users [:int {:min 0}]]
   [:media-bytes [:int {:min 0}]]])

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
  [{:keys [log-level smtp s3 mail]}]
  (cond-> {:sepal.logging.interface/logging {:level log-level}
           :sepal.malli.interface/init {}}

    (and smtp (not mail))
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
     :mail (or (:mail opts) (:sepal.mail.interface/client system))
     :s3-client (:sepal.aws-s3.interface/s3-client system)
     :s3-presigner (:sepal.aws-s3.interface/s3-presigner system)
     ;; Everything already running in this process that no two instances may
     ;; share. Two instances on one database silently merge two gardens; two on
     ;; one slug silently share a cookie key and token secret; two on one backup
     ;; directory, media cache directory or media key prefix each read the
     ;; other's archives, derivatives or S3 objects. None of it is detectable
     ;; after the fact, so all of it is refused at start!.
     :registry (atom {:slugs #{}
                      :db-paths #{}
                      :backup-dirs #{}
                      :media-cache-dirs #{}
                      :media-key-prefixes #{}})}))

(defn stop-process! [process]
  (ig/halt! (:system process))
  nil)

;; The request context's defaults for invitation mail. Named because invite-owner!
;; resolves them too and the two must not drift: one address sends for every
;; garden, so a per-garden default appearing here would be a change to how mail is
;; sent and not a detail.
(def ^:private default-invitation-email-from "noreply@sepal.app")
(def ^:private default-invitation-email-subject "You've been invited to Sepal")

(defn- instance-config
  [process {:keys [slug db-path app-domain media-key-prefix media-cache-dir media-cache-size-mb backup-dir
                   start-server? jetty-host jetty-port
                   vite hot-reload reload-per-request?
                   forgot-password-email-from forgot-password-email-subject
                   invitation-email-from invitation-email-subject]}]
  (cond->
    {:sepal.token.interface/service
     {:secret (token-secret (:master-secret process) slug)}

     :sepal.media-transform.interface/service
   ;; Per instance, not shared: cache-key is SHA-256 over a per-database row id,
   ;; so two gardens sharing one cache directory would serve each other's
   ;; derivatives for the same id. Separate directories remove the collision.
     {:cache-dir media-cache-dir
      :max-cache-size-mb (or media-cache-size-mb 500)}

     :sepal.app.server/zodiac-sql
   ;; No :schema-dump-file: ::zodiac-sql no longer loads a schema on its own.
   ;; provision! owns schema loading. Pragmas and extensions come from
   ;; sepal.database.interface, so every pool in the process agrees.
     {:database-path db-path
      :extension-library-path (:extensions-library-path process)
      :context-key :db}

     :sepal.app.server/zodiac-assets
   ;; :vite is emitted explicitly even when the caller omits it — an omitted
   ;; :vite means {:mode :build} to zodiac-assets, which runs npm and vite once
   ;; per instance. Only the REPL passes a value.
     {:manifest-path "app/build/.vite/manifest.json"
      :asset-resource-path "app/build/assets"
      :cache-manifest? true
      :vite vite}

     :sepal.app.server/zodiac
     {:extensions (cond-> [(ig/ref :sepal.app.server/zodiac-sql)
                           (ig/ref :sepal.app.server/zodiac-assets)]
                    hot-reload (conj (ig/ref :sepal.app.server/zodiac-hot-reload)))
      :cookie-secret (cookie-key (:master-secret process) slug)
      :start-server? (boolean start-server?)
      :reload-per-request? (boolean reload-per-request?)
      :jetty {:host (or jetty-host "0.0.0.0") :port (or jetty-port 3000)}
      :request-context {:app-domain app-domain
                        :mail (:mail process)
                        :token-service (ig/ref :sepal.token.interface/service)
                        :s3-client (:s3-client process)
                        :s3-presigner (:s3-presigner process)
                        :media-transform-service (ig/ref :sepal.media-transform.interface/service)
                        :media-upload-bucket (:media-upload-bucket process)
                        :media-key-prefix media-key-prefix
                        :backup-dir backup-dir
                        :forgot-password-email-from (or forgot-password-email-from "support@sepal.app")
                        :forgot-password-email-subject (or forgot-password-email-subject "Sepal - Reset Password")
                        :invitation-email-from (or invitation-email-from default-invitation-email-from)
                        :invitation-email-subject (or invitation-email-subject default-invitation-email-subject)}}

     :sepal.scheduler.interface/scheduler {}

     :sepal.app.backup/job
     {:scheduler (ig/ref :sepal.scheduler.interface/scheduler)
      :zodiac (ig/ref :sepal.app.server/zodiac)
      :mail (:mail process)
      :app-domain app-domain
      :backup-dir backup-dir}}

    hot-reload
    (assoc :sepal.app.server/zodiac-hot-reload hot-reload)))

(defn- canonical-path
  [path]
  (str (fs/canonicalize path {:nofollow-links true})))

(defn- claim!
  "Record everything this instance takes exclusive use of, or throw. Paths are
  compared canonically so two spellings of one directory are one claim. Held
  under a lock rather than inside swap! because the check and the write must not
  be retried independently. Returns the claim, for release!."
  [process {:keys [slug db-path backup-dir media-cache-dir media-key-prefix]}]
  (let [db (canonical-path db-path)
        backups (canonical-path backup-dir)
        cache (canonical-path media-cache-dir)
        claim {:slug slug
               :db-path db
               :backup-dir backups
               :media-cache-dir cache
               :media-key-prefix media-key-prefix}
        registry (:registry process)]
    (locking registry
      (let [{:keys [slugs db-paths backup-dirs media-cache-dirs media-key-prefixes]} @registry]
        (when (contains? slugs slug)
          (throw (ex-info (format "Slug %s is already running in this process" slug)
                          {:reason :duplicate-slug :slug slug})))
        (when (contains? db-paths db)
          (throw (ex-info (format "Database %s is already open in this process" db)
                          {:reason :duplicate-database :slug slug :db-path db})))
        (when (contains? backup-dirs backups)
          (throw (ex-info (format "Backup directory %s is already in use in this process" backups)
                          {:reason :duplicate-backup-dir :slug slug :backup-dir backups})))
        (when (contains? media-cache-dirs cache)
          (throw (ex-info (format "Media cache directory %s is already in use in this process" cache)
                          {:reason :duplicate-media-cache-dir :slug slug :media-cache-dir cache})))
        ;; Overlap, not equality: own-key? matches with str/starts-with?, so an
        ;; instance holding "a/" would also accept the objects of one holding
        ;; "a/nested/".
        (when-let [other (some #(when (or (str/starts-with? % media-key-prefix)
                                          (str/starts-with? media-key-prefix %))
                                  %)
                               media-key-prefixes)]
          (throw (ex-info (format "Media key prefix %s overlaps %s, already in use in this process"
                                  media-key-prefix other)
                          {:reason :overlapping-media-key-prefix
                           :slug slug
                           :media-key-prefix media-key-prefix
                           :other-media-key-prefix other})))
        (swap! registry #(-> % (update :slugs conj slug)
                             (update :db-paths conj db)
                             (update :backup-dirs conj backups)
                             (update :media-cache-dirs conj cache)
                             (update :media-key-prefixes conj media-key-prefix)))))
    claim))

(defn- release!
  [process claim]
  (swap! (:registry process)
         #(-> % (update :slugs disj (:slug claim))
              (update :db-paths disj (:db-path claim))
              (update :backup-dirs disj (:backup-dir claim))
              (update :media-cache-dirs disj (:media-cache-dir claim))
              (update :media-key-prefixes disj (:media-key-prefix claim)))))

(defn- sql-failure?
  "Whether a SQLException appears anywhere in the cause chain."
  [^Throwable e]
  (boolean (some #(instance? SQLException %)
                 (take-while some? (iterate #(.getCause ^Throwable %) e)))))

(defn- init-failure
  "Reclassify integrant's :integrant.core/build-threw-exception, which tells a
  caller nothing. The backup job queries the instance database during ig/init,
  ahead of probe!, so a pooled-connection failure lands here — but a component
  that simply would not build is not a database problem, and is not labelled as
  one. Keeps integrant's key and partial system, and the original as the cause."
  [slug db-path e]
  (let [{:keys [key system]} (ex-data e)
        database? (sql-failure? e)]
    (ex-info (if database?
               (format "Database %s could not be opened" db-path)
               ;; integrant's message names the key it failed on.
               (format "Instance %s could not be built: %s" slug (ex-message e)))
             {:reason (if database? :database-unusable :instance-init-failed)
              :slug slug
              :db-path db-path
              :key key
              :system system}
             e)))

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
  (let [claim (claim! process opts)]
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
                       (throw (init-failure slug db-path e))))]
        (try
          (probe! system)
          {:slug slug
           :db-path db-path
           :claim claim
           :process process
           :system system
           ;; Kept because zodiac closes its :request-context over middleware
           ;; rather than putting it in the system map, so app-domain and the
           ;; invitation mail settings are unreadable from a started instance.
           ;; invite-owner! needs all three.
           :opts opts}
          (catch Throwable e
            (ig/halt! system)
            (throw (ex-info (format "Database %s could not be opened" db-path)
                            {:reason :database-unusable :slug slug :db-path db-path}
                            e)))))
      (catch Throwable e
        (release! process claim)
        (throw e)))))

(defn handler
  "The ring handler for an instance."
  [instance]
  (get-in instance [:system :sepal.app.server/zodiac ::z/app]))

(defn stop! [instance]
  (let [{:keys [process claim]} instance]
    (ig/halt! (:system instance))
    (when process
      (release! process claim)))
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

(defn- random-password
  "A 32-character password that is never revealed. The owner sets their own
  through the accept link, exactly as an invited user does in the app."
  []
  (let [chars "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"]
    (apply str (repeatedly 32 #(rand-nth chars)))))

(defn- accept-url
  "The absolute accept-invitation URL for one garden.

  The path comes from the route table rather than a literal, which means binding
  a router: z/url-for reads the one bound to the current request, and there is no
  request here. ::z/router is a factory, so this builds a router per call — once
  per invitation, which is rare."
  [instance token]
  (let [router-factory (get-in instance [:system :sepal.app.server/zodiac ::z/router])
        app-domain (get-in instance [:opts :app-domain])]
    (when-not (fn? router-factory)
      (throw (ex-info "No router on this instance, so no accept URL can be built"
                      {:reason :router-unavailable :slug (:slug instance)})))
    (binding [z/*router* (router-factory)]
      (format "https://%s%s"
              app-domain
              (z/url-for auth.routes/accept-invitation nil {:token token})))))

(defn- send-owner-invitation-email
  [mail {:keys [to accept-url from subject]}]
  (let [content (mustache/render-resource "app/email/owner-invitation.mustache"
                                          {:accept-url accept-url})]
    (mail.i/send-message mail {:from from
                               :to to
                               :subject subject
                               :body content})))

(defn invite-owner!
  "Create the owner of a managed garden and mail them a link to set their own
  password. Returns {:user-id ... :accept-url ...}.

  No password is passed in and none is returned: the user is created with a random
  one that is never revealed, and the accept link is how a password gets set. The
  user is :invited rather than :active, because that is the status
  sepal.app.routes.auth.accept-invitation requires before it will honour the link.

  Completing setup is part of the contract. A garden created this way is
  configured by whoever hosts it, not by its owner, so the setup wizard has
  nothing to ask — and sepal.app.middleware forces that wizard until
  setup.completed_at is set.

  The sender is one address for every garden, defaulting to noreply@sepal.app:
  SPF does not inherit to subdomains, so a per-garden sender would need its own
  record and would fragment sending reputation.

  Calling it again for the same address resends. A first send can fail — a mail
  provider misconfigured, a network blip — and throwing on the second call would
  leave that garden permanently unenterable, since the owner exists and no link
  ever arrived. An owner who has already activated is a different matter and does
  throw."
  [instance {:keys [email]}]
  (let [db (instance-db instance)
        mail (get-in instance [:process :mail])
        token-service (get-in instance [:system :sepal.token.interface/service])
        {:keys [invitation-email-from invitation-email-subject]} (:opts instance)]
    ;; Checked before the user is created, not after: an owner row with no
    ;; invitation sent is a garden nobody can enter.
    (when (nil? mail)
      (throw (ex-info "This process has no mail client, so no owner can be invited"
                      {:reason :mail-not-configured :email email :slug (:slug instance)})))
    (let [existing (user.i/get-by-email db email)
          _ (when (and existing (not= :invited (:user/status existing)))
              (throw (ex-info (format "A user already exists for %s and has activated" email)
                              {:reason :user-active :email email :slug (:slug instance)})))
          user (or existing
                   (let [created (user.i/create! db {:email email
                                                     :password (random-password)
                                                     :role :admin
                                                     :status :invited})]
                     (when (error.i/error? created)
                       (throw (ex-info "Could not create the owner"
                                       {:reason :create-user-failed :error created :email email})))
                     created))
          token (token.i/encode token-service {:email email
                                               :expires-at (token.i/expires-in-hours 24)})
          url (accept-url instance token)]
      ;; Before the mail, deliberately. Whether a message arrived has nothing to do
      ;; with whether this garden is configured, and a provider that refused once
      ;; used to leave a fully provisioned garden showing a wizard its owner cannot
      ;; act on — found in production.
      (setup.shared/complete-setup! db)
      (send-owner-invitation-email mail
                                   {:to email
                                    :accept-url url
                                    :from (or invitation-email-from
                                              default-invitation-email-from)
                                    :subject (or invitation-email-subject
                                                 default-invitation-email-subject)})
      {:user-id (:user/id user) :accept-url url})))

(defn usage
  "The countable things in a running instance, for tier accounting by a caller
  that hosts it. One map rather than a family of getters, validated before it is
  returned so a caller cannot receive a shape that quietly changed.

  Takes the instance rather than a path, like `create-admin-user!`: the caller
  never holds a database handle, and this reads through the pool the instance
  already owns. Media bytes are summed from the rows here rather than listed from
  object storage, so this makes no network call."
  [instance]
  (let [db (instance-db instance)
        result {:accessions (accession.i/count-all db)
                :materials (material.i/count-all db)
                :users (user.i/count-all db)
                :media-bytes (media.i/total-size-in-bytes db)}]
    (validate! Usage result "usage")
    result))
