(ns sepal.app.instance-test
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [integrant.core :as ig]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [peridot.core :as peri]
            [sepal.app.backup.core :as backup]
            [sepal.app.instance :as instance]
            [sepal.database.interface :as db.i]
            [sepal.mail.interface.protocols :as mail.p]
            [sepal.media-transform.interface :as media-transform.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]))

(def ^:private master "master-secret-for-tests")

(deftest test-cookie-key
  (testing "is 16 bytes"
    (is (= 16 (count (#'instance/cookie-key master "brooklyn")))))

  (testing "is stable for the same master and slug"
    (is (= (seq (#'instance/cookie-key master "brooklyn"))
           (seq (#'instance/cookie-key master "brooklyn")))))

  (testing "differs across slugs"
    (is (not= (seq (#'instance/cookie-key master "brooklyn"))
              (seq (#'instance/cookie-key master "queens")))))

  (testing "differs across masters"
    (is (not= (seq (#'instance/cookie-key "master-a-for-tests" "brooklyn"))
              (seq (#'instance/cookie-key "master-b-for-tests" "brooklyn"))))))

(deftest test-token-secret
  (testing "is a 64 character lowercase hex string"
    (let [secret (#'instance/token-secret master "brooklyn")]
      (is (string? secret))
      (is (= 64 (count secret)))
      (is (re-matches #"[0-9a-f]{64}" secret))))

  (testing "is stable for the same master and slug, and differs across slugs"
    (is (= (#'instance/token-secret master "brooklyn")
           (#'instance/token-secret master "brooklyn")))
    (is (not= (#'instance/token-secret master "brooklyn")
              (#'instance/token-secret master "queens"))))

  (testing "is independent of the cookie key for the same slug"
    (is (not= (seq (#'instance/cookie-key master "brooklyn"))
              (seq (take 16 (.getBytes ^String (#'instance/token-secret master "brooklyn")
                                       "UTF-8")))))))

(deftest test-hkdf-matches-rfc-5869
  ;; RFC 5869 Test Case 1. Verified against this implementation on JDK 26.
  (testing "the basic SHA-256 test case"
    (let [ikm (byte-array (repeat 22 (unchecked-byte 0x0b)))
          salt (byte-array (map unchecked-byte (range 0x00 0x0d)))
          info (byte-array (map unchecked-byte [0xf0 0xf1 0xf2 0xf3 0xf4
                                                0xf5 0xf6 0xf7 0xf8 0xf9]))
          okm (#'instance/hkdf ikm salt info 42)]
      (is (= (str "3cb25f25faacd57a90434f64d0362f2a"
                  "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                  "34007208d5b887185865")
             (#'instance/->hex okm))))))

(def ^:private valid-instance-opts
  {:slug "brooklyn"
   :db-path "/tmp/sepal-nonexistent/sepal.db"
   :app-domain "brooklyn.sepal.app"
   :media-key-prefix "brooklyn/"
   :media-cache-dir "/tmp/sepal-nonexistent/cache"
   :backup-dir "/tmp/sepal-nonexistent/backups"})

(deftest test-instance-opts-are-closed
  (testing "a complete opts map validates"
    (is (nil? (#'instance/validate! instance/InstanceOpts valid-instance-opts "instance opts"))))

  (testing "an unknown key is rejected rather than ignored"
    (let [thrown (try
                   (#'instance/validate! instance/InstanceOpts
                                         (assoc valid-instance-opts :db-paht "/oops")
                                         "instance opts")
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (some? thrown) "a typo'd key must not be silently ignored")
      (is (= :invalid-opts (:reason (ex-data thrown))))))

  (testing "each tenant-bearing key is required"
    (doseq [k [:slug :db-path :app-domain :media-key-prefix :media-cache-dir :backup-dir]]
      (let [thrown (try
                     (#'instance/validate! instance/InstanceOpts
                                           (dissoc valid-instance-opts k)
                                           "instance opts")
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (some? thrown) (str k " must be required")))))

  (testing "a slug that would be unsafe as a salt or an S3 prefix is rejected"
    (doseq [slug ["" "../etc" "Brooklyn" "brook lyn" "brooklyn/"]]
      (let [thrown (try
                     (#'instance/validate! instance/InstanceOpts
                                           (assoc valid-instance-opts :slug slug)
                                           "instance opts")
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (some? thrown) (str "slug " (pr-str slug) " must be rejected")))))

  (testing "a media-key-prefix without a trailing slash is rejected"
    ;; own-key? refuses a key with str/starts-with? on the raw prefix, so a
    ;; slashless prefix like "brooklyn" would also accept "brooklynheights/...".
    (let [thrown (try
                   (#'instance/validate! instance/InstanceOpts
                                         (assoc valid-instance-opts :media-key-prefix "brooklyn")
                                         "instance opts")
                   nil
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (some? thrown) "a prefix without a trailing slash must be rejected")
      (is (= :invalid-opts (:reason (ex-data thrown)))))))

(deftest test-process-opts-are-closed
  (testing "a master secret of at least 16 characters is required"
    (is (nil? (#'instance/validate! instance/ProcessOpts
                                    {:master-secret "master-secret-for-tests"}
                                    "process opts")))
    (doseq [opts [{} {:master-secret "short"} {:master-secret "master-secret-for-tests"
                                               :log-levle "INFO"}]]
      (let [thrown (try
                     (#'instance/validate! instance/ProcessOpts opts "process opts")
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (some? thrown) (str (pr-str opts) " must be rejected"))))))

(defn- table-names [db-path]
  (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-path)})]
    (->> (jdbc/execute! ds ["select name from sqlite_master where type = 'table'"])
         (map (comp first vals))
         set)))

(deftest test-provision
  (testing "a fresh path yields a database with the schema loaded"
    (let [dir (fs/create-temp-dir {:prefix "sepal-provision"})
          db-path (str (fs/path dir "garden" "sepal.db"))]
      (try
        (is (= {:db-path db-path}
               (instance/provision! {:db-path db-path})))
        (is (fs/exists? db-path))
        (let [tables (table-names db-path)]
          (is (contains? tables "user"))
          (is (contains? tables "accession"))
          (is (contains? tables "schema_version")))
        (finally
          (fs/delete-tree dir)))))

  (testing "an existing database is refused, untouched"
    (let [dir (fs/create-temp-dir {:prefix "sepal-provision"})
          db-path (str (fs/path dir "sepal.db"))]
      (try
        (spit db-path "not a database")
        (let [thrown (try
                       (instance/provision! {:db-path db-path})
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "provision! should have thrown")
          (is (= :database-exists (:reason (ex-data thrown)))))
        (is (= "not a database" (slurp db-path)))
        (finally
          (fs/delete-tree dir))))))

(defrecord RecordingMailClient [sent]
  mail.p/MailClient
  (send-message [_ message]
    (swap! sent conj message)
    {:status :sent}))

(defn- with-two-gardens
  "Provision two gardens in one temp dir, start a process and both instances,
  call (f process instance-a instance-b), then tear everything down."
  [f]
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (instance/start-process!
                  {:log-level "WARN"
                   :master-secret "master-secret-for-tests"
                   ;; A client rather than none: invite-owner! refuses a process
                   ;; that cannot mail anyone. Reachable as (:sent (:mail process)).
                   :mail (->RecordingMailClient (atom []))
                   :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})
        start (fn [slug]
                (let [db-path (str (fs/path dir slug "sepal.db"))]
                  (instance/provision! {:db-path db-path})
                  (instance/start! process {:slug slug
                                            :db-path db-path
                                            :app-domain (str slug ".localhost")
                                            :media-key-prefix (str slug "/")
                                            :media-cache-dir (str (fs/path dir slug "cache"))
                                            :backup-dir (str (fs/path dir slug "backups"))})))
        a (start "a")
        b (start "b")]
    (try
      (f process a b)
      (finally
        (instance/stop! b)
        (instance/stop! a)
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-two-instances-in-one-jvm
  (with-two-gardens
    (fn [_process a b]
      (testing "each instance exposes its own handler"
        (is (fn? (instance/handler a)))
        (is (not= (instance/handler a) (instance/handler b))))

      (testing "both handlers route and serve, each against its own database"
        ;; /ok is excluded from the setup redirect, so a 204 here means the
        ;; router and the middleware chain really ran for this instance.
        (doseq [instance [a b]]
          (let [response (:response (-> (peri/session (instance/handler instance))
                                        (peri/request "/ok")))]
            (is (= 204 (:status response))))))

      (testing "a garden with setup incomplete redirects to the wizard"
        ;; Auth routes are deliberately not excluded from this redirect
        ;; (middleware.clj:186-194), so /login redirects too until setup is done.
        (doseq [instance [a b]
                path ["/" "/login"]]
          (let [response (:response (-> (peri/session (instance/handler instance))
                                        (peri/request path)))]
            (is (= 303 (:status response)))
            (is (= "/setup" (get-in response [:headers "Location"])))))))))

(deftest test-start-refuses-a-missing-database
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (instance/start-process! {:log-level "WARN"
                                          :master-secret "master-secret-for-tests"})]
    (try
      (let [thrown (try
                     (instance/start! process {:slug "ghost"
                                               :db-path (str (fs/path dir "nope.db"))
                                               :app-domain "ghost.localhost"
                                               :media-key-prefix "ghost/"
                                               :media-cache-dir (str (fs/path dir "ghost" "cache"))
                                               :backup-dir (str (fs/path dir "ghost" "backups"))})
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (some? thrown) "start! should have thrown")
        (is (= :database-missing (:reason (ex-data thrown)))))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-media-cache-is-per-instance
  (with-two-gardens
    (fn [process a b]
      (testing "the process holds no media cache"
        (is (nil? (:media-transform-service process))))

      (testing "each instance has its own cache directory and cache database"
        (let [cache-of (fn [instance]
                         (get-in instance [:system :sepal.media-transform.interface/service]))
              cache-a (cache-of a)
              cache-b (cache-of b)]
          (is (not= (:cache-dir cache-a) (:cache-dir cache-b)))
          (is (not= (:cache-ds cache-a) (:cache-ds cache-b)))

          (testing "and an entry in one is invisible to the other"
            ;; The same media id and params in two gardens hash identically;
            ;; separate cache databases are what keeps them apart.
            (let [hash (media-transform.i/cache-key 1 {:width 200})]
              (media-transform.i/put-entry! (:cache-ds cache-a)
                                            {:hash hash :media-id 1 :size-bytes 123})
              (is (some? (media-transform.i/get-entry (:cache-ds cache-a) hash)))
              (is (nil? (media-transform.i/get-entry (:cache-ds cache-b) hash))
                  "garden B must not see garden A's cached derivative"))))))))

(deftest test-backup-directory-is-per-instance
  (with-two-gardens
    (fn [_process a b]
      (testing "each instance reports its own backup directory in its context"
        ;; The directory is visible on the started backup job, which is the
        ;; component that writes archives.
        (is (not= (get-in a [:system :sepal.app.backup/job :backup-dir])
                  (get-in b [:system :sepal.app.backup/job :backup-dir])))
        (is (some? (get-in a [:system :sepal.app.backup/job :backup-dir])))))))

(defn- garden-opts
  "Complete instance opts for slug rooted under dir."
  [dir slug]
  {:slug slug
   :db-path (str (fs/path dir slug "sepal.db"))
   :app-domain (str slug ".localhost")
   :media-key-prefix (str slug "/")
   :media-cache-dir (str (fs/path dir slug "cache"))
   :backup-dir (str (fs/path dir slug "backups"))})

(defn- test-process []
  (instance/start-process! {:log-level "WARN"
                            :master-secret "master-secret-for-tests"
                            :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")}))

(deftest test-start-refuses-a-second-instance-on-one-database
  (with-two-gardens
    (fn [process a b]
      (testing "the same database twice in one process is refused"
        (let [thrown (try
                       (instance/start! process (assoc (garden-opts "/tmp" "c")
                                                       :db-path (:db-path a)))
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "two instances on one database must be refused")
          (is (= :duplicate-database (:reason (ex-data thrown))))))

      (testing "the same slug twice in one process is refused"
        ;; b's database is real and current, so start! gets past the existence
        ;; and version checks and reaches the slug claim.
        (let [thrown (try
                       (instance/start! process (assoc (garden-opts "/tmp" (:slug a))
                                                       :db-path (:db-path b)))
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "two instances on one slug must be refused")
          (is (= :duplicate-slug (:reason (ex-data thrown)))))))))

(deftest test-start-refuses-a-shared-directory-or-media-key-prefix
  ;; The control plane derives these three from the slug, so they are distinct
  ;; today by convention. A shared backup directory would list and serve one
  ;; garden's archives from another's /settings/backups; a shared media cache
  ;; would serve one garden's derivatives for the other's row ids; an
  ;; overlapping key prefix would make own-key? accept the other's objects.
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (test-process)
        a (garden-opts dir "a")
        b (garden-opts dir "b")
        refused (fn [opts]
                  (try
                    (instance/stop! (instance/start! process opts))
                    nil
                    (catch clojure.lang.ExceptionInfo e e)))]
    (try
      (instance/provision! {:db-path (:db-path a)})
      (instance/provision! {:db-path (:db-path b)})
      (let [started (instance/start! process a)]
        (try
          (testing "a second instance on the first's backup directory is refused"
            (let [thrown (refused (assoc b :backup-dir (:backup-dir a)))]
              (is (some? thrown) "a shared backup directory must be refused")
              (is (= :duplicate-backup-dir (:reason (ex-data thrown))))))

          (testing "another spelling of the same backup directory is still refused"
            (let [thrown (refused (assoc b :backup-dir (str (fs/path dir "b" ".." "a" "backups"))))]
              (is (some? thrown) "the claim must compare canonical paths")
              (is (= :duplicate-backup-dir (:reason (ex-data thrown))))))

          (testing "a second instance on the first's media cache directory is refused"
            (let [thrown (refused (assoc b :media-cache-dir (:media-cache-dir a)))]
              (is (some? thrown) "a shared media cache directory must be refused")
              (is (= :duplicate-media-cache-dir (:reason (ex-data thrown))))))

          (testing "a media key prefix that overlaps the first's is refused"
            (doseq [prefix [(:media-key-prefix a) (str (:media-key-prefix a) "nested/")]]
              (let [thrown (refused (assoc b :media-key-prefix prefix))]
                (is (some? thrown) (str "prefix " (pr-str prefix) " must be refused"))
                (is (= :overlapping-media-key-prefix (:reason (ex-data thrown)))))))

          (testing "a garden whose own values are all distinct still starts"
            (let [started-b (instance/start! process b)]
              (is (fn? (instance/handler started-b)))
              (instance/stop! started-b)))
          (finally
            (instance/stop! started))))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-stopping-releases-the-claim
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (test-process)
        opts (garden-opts dir "a")]
    (try
      (instance/provision! {:db-path (:db-path opts)})
      (testing "a stopped instance can be started again"
        (instance/stop! (instance/start! process opts))
        (let [restarted (instance/start! process opts)]
          (is (fn? (instance/handler restarted)))
          (instance/stop! restarted)))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-a-failed-init-halts-the-partial-system-and-releases-the-claim
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (test-process)
        opts (garden-opts dir "leaky")]
    (try
      (instance/provision! {:db-path (:db-path opts)})
      (testing "a late init-key throwing halts what was already built and releases the claim"
        (let [thrown (with-redefs [backup/register-backup-job! (fn [& _] (throw (ex-info "boom" {})))]
                       (try
                         (instance/start! process opts)
                         nil
                         (catch clojure.lang.ExceptionInfo e e)))]
          (is (some? thrown) "start! should have thrown")
          (testing "the failure is reported as a build failure, naming the key"
            ;; integrant's own :integrant.core/build-threw-exception tells the
            ;; control plane's dispatcher nothing when it logs :reason.
            (is (= :instance-init-failed (:reason (ex-data thrown))))
            (is (= :sepal.app.backup/job (:key (ex-data thrown))))
            (is (= "boom" (ex-message (ex-cause (ex-cause thrown))))
                "the original exception must survive as a cause"))
          (testing "the pool from the partially built system is dead, not leaked"
            (let [partial-system (:system (ex-data thrown))
                  db (get-in partial-system [:sepal.app.server/zodiac :zodiac.ext.sql/db])]
              (is (some? db) "the partially built system should still include the connection pool")
              (is (thrown? Exception (jdbc/execute-one! db ["select 1"]))
                  "a halted pool must refuse queries")))))

      (testing "a query failing inside ig/init is reported as an unusable database"
        ;; The backup job reads settings during ig/init, ahead of probe!, so a
        ;; pooled-connection failure (a missing SpatiaLite extension, say)
        ;; surfaces here rather than at probe!.
        (let [thrown (with-redefs [backup/register-backup-job!
                                   (fn [& _]
                                     (throw (java.sql.SQLException. "no such function: load_extension")))]
                       (try
                         (instance/start! process opts)
                         nil
                         (catch clojure.lang.ExceptionInfo e e)))]
          (is (some? thrown) "start! should have thrown")
          (is (= :database-unusable (:reason (ex-data thrown))))))

      (testing "the slug and database were released, so starting again succeeds"
        (let [restarted (instance/start! process opts)]
          (is (fn? (instance/handler restarted)))
          (instance/stop! restarted)))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-start-refuses-a-database-behind-the-code
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (test-process)
        opts (garden-opts dir "old")]
    (try
      (instance/provision! {:db-path (:db-path opts)})
      ;; Rewind the database one migration behind the code.
      (let [ds (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" (:db-path opts))})]
        (jdbc/execute! ds ["delete from schema_version where version = ?"
                           (db.i/latest-version)]))
      (testing "a database behind the code refuses to start"
        (let [thrown (try
                       (instance/start! process opts)
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "start! should refuse an out-of-date database")
          (is (= :schema-version-behind (:reason (ex-data thrown))))
          (is (= (db.i/latest-version) (:expected (ex-data thrown))))))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-start-refuses-an-unusable-database
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (test-process)
        opts (garden-opts dir "broken")]
    (try
      ;; A provisioned database, then corrupted in place.
      (instance/provision! {:db-path (:db-path opts)})
      (spit (:db-path opts) "this is not a sqlite database")
      (testing "a corrupt database fails at start!, not on the first request"
        (let [thrown (try
                       (instance/start! process opts)
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "start! should refuse a corrupt database")
          (is (contains? #{:database-unusable :schema-version-behind}
                         (:reason (ex-data thrown))))))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-schema-lifecycle-through-the-instance-api
  (let [dir (fs/create-temp-dir {:prefix "sepal-schema-api"})
        db-path (str (fs/path dir "sepal.db"))]
    (try
      (instance/provision! {:db-path db-path})
      (testing "a provisioned database is at the latest version"
        (is (= (instance/latest-schema-version)
               (instance/schema-version {:db-path db-path}))))

      (testing "preflight! passes and migrate! is a no-op when nothing is pending"
        (is (:ok? (instance/preflight! {:db-path db-path})))
        (is (= {:applied []} (instance/migrate! {:db-path db-path}))))
      (finally
        (fs/delete-tree dir)))))

(defn- follow-all
  "Follow up to n redirects and return the final response."
  [session n]
  (loop [session session
         n n]
    (let [response (:response session)]
      (if (and (pos? n) (#{301 302 303 307 308} (:status response)))
        (recur (peri/follow-redirect session) (dec n))
        response))))

(defn- login
  "Log in through a handler and return the peridot session."
  [app email password]
  (let [{:keys [response] :as session} (-> (peri/session app)
                                           (peri/request "/login"))
        token (test.i/response-anti-forgery-token response)]
    (-> session
        (peri/request "/login"
                      :request-method :post
                      :params {:__anti-forgery-token token
                               :email email
                               :password password}))))

(deftest test-create-admin-user
  (with-two-gardens
    (fn [_process a b]
      (testing "creating the admin completes setup, so the login page is served"
        (is (= {:user-id 1} (instance/create-admin-user!
                              a {:email "admin@a.example.com"
                                 :password "a-password"})))
        (let [response (:response (-> (peri/session (instance/handler a))
                                      (peri/request "/login")))]
          (is (= 200 (:status response)))
          (is (re-find #"(?i)password" (str (:body response))))))

      (testing "the admin can log in at its own garden"
        (let [session (login (instance/handler a) "admin@a.example.com" "a-password")]
          (is (not= "/login" (get-in session [:response :headers "Location"]))
              "a rejected login bounces back to /login")
          (is (= 200 (:status (follow-all session 5))))))

      (testing "the other garden is untouched — still no user, still in setup"
        (let [response (:response (-> (peri/session (instance/handler b))
                                      (peri/request "/login")))]
          (is (= 303 (:status response)))
          (is (= "/setup" (get-in response [:headers "Location"])))))

      (testing "a session minted at one garden is not accepted at the other"
        (instance/create-admin-user! b {:email "admin@b.example.com"
                                        :password "b-password"})
        (let [session (login (instance/handler a) "admin@a.example.com" "a-password")
              cookie (test.i/ring-session-cookie session)
              crossed (-> (peri/session (instance/handler b))
                          (assoc :cookie-jar (:cookie-jar session))
                          (peri/request "/"))]
          (is (some? cookie) "instance A should have set a session cookie")
          (is (= 303 (:status (:response crossed))))
          (is (re-find #"/login" (get-in crossed [:response :headers "Location"])))))

      (testing "credentials from one garden do not work at the other"
        (let [response (:response (login (instance/handler b)
                                         "admin@a.example.com"
                                         "a-password"))]
          ;; A rejected login goes back to /login; an accepted one goes to /.
          (is (= "/login" (get-in response [:headers "Location"])))))

      (testing "a second admin for the same email is refused"
        (let [thrown (try
                       (instance/create-admin-user! a {:email "admin@a.example.com"
                                                       :password "a-password"})
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "create-admin-user! should have thrown")
          (is (= :user-exists (:reason (ex-data thrown)))))))))

(deftest test-invite-owner
  (with-two-gardens
    (fn [_process a b]
      (let [result (instance/invite-owner! a {:email "owner@example.com"})]

        (testing "an invited admin user exists for that address"
          (is (pos-int? (:user-id result))))

        (testing "the accept url points at this garden, not a configured host"
          ;; With no card collected at signup the invitation is the only way into
          ;; a garden, so a link to the wrong host is a customer who cannot get in.
          (is (str/starts-with? (:accept-url result) "https://a.localhost/"))
          (is (str/includes? (:accept-url result) "token=")))

        (testing "setup is complete, so the wizard is skipped"
          ;; This is the whole mechanism by which a managed garden has no wizard.
          (let [response (:response (-> (peri/session (instance/handler a))
                                        (peri/request "/login")))]
            (is (= 200 (:status response)))))

        (testing "the other garden is untouched — still in setup"
          (let [response (:response (-> (peri/session (instance/handler b))
                                        (peri/request "/login")))]
            (is (= 303 (:status response)))
            (is (= "/setup" (get-in response [:headers "Location"])))))

        (testing "the accept link is one accept_invitation.clj will honour"
          ;; It reads :email out of the token and requires the user to be
          ;; :invited, so a token carrying anything else is a dead link.
          (let [response (:response (-> (peri/session (instance/handler a))
                                        (peri/request (str/replace (:accept-url result)
                                                                   "https://a.localhost" ""))))]
            (is (= 200 (:status response)))
            (is (re-find #"(?i)accept invitation" (str (:body response))))))

        (testing "inviting the same address again resends rather than refusing"
          ;; The first send can fail — a mail provider misconfigured, a network
          ;; blip — and the owner is then a user with no way in. Throwing on the
          ;; second call left that garden unrecoverable. An invited user may be
          ;; resent, which is the same rule resend_invitation.clj applies.
          (let [again (instance/invite-owner! a {:email "owner@example.com"})]
            (is (= (:user-id result) (:user-id again)) "the same user, not a second one")
            (is (str/starts-with? (:accept-url again) "https://a.localhost/"))))

        (testing "but an owner who has already activated is a real conflict"
          (let [db (#'instance/instance-db a)
                thrown (do (user.i/activate! db (:user-id result))
                           (try (instance/invite-owner! a {:email "owner@example.com"})
                                nil
                                (catch clojure.lang.ExceptionInfo e e)))]
            (is (some? thrown) "invite-owner! should have thrown")
            (is (= :user-active (:reason (ex-data thrown))))))))))

(deftest test-complete-setup
  (with-two-gardens
    (fn [_process a b]
      (testing "until it runs, even /login is forced to the setup wizard"
        ;; Which is the whole risk: the wizard's POST creates an active admin
        ;; from an anonymous request, so a garden left here belongs to whoever
        ;; finds the hostname.
        (let [response (:response (-> (peri/session (instance/handler a))
                                      (peri/request "/login")))]
          (is (= 303 (:status response)))
          (is (= "/setup" (get-in response [:headers "Location"])))))

      (testing "after it, /login serves"
        (is (nil? (instance/complete-setup! a)))
        (let [response (:response (-> (peri/session (instance/handler a))
                                      (peri/request "/login")))]
          (is (= 200 (:status response)))
          (is (re-find #"(?i)password" (str (:body response))))))

      (testing "and it invited nobody, so the garden still has no users"
        ;; Configuring a garden and handing it to its owner are separate steps.
        ;; This is the one provisioning owes; the invitation comes later.
        (is (= 0 (:users (instance/usage a)))))

      (testing "calling it again is harmless — it is an upsert of one setting"
        (is (nil? (instance/complete-setup! a)))
        (is (= 200 (:status (:response (-> (peri/session (instance/handler a))
                                           (peri/request "/login")))))))

      (testing "the other garden is untouched — still in setup"
        (let [response (:response (-> (peri/session (instance/handler b))
                                      (peri/request "/login")))]
          (is (= 303 (:status response)))
          (is (= "/setup" (get-in response [:headers "Location"]))))))))

(deftest test-dev-opts-default-to-production-shapes
  (testing "omitted, :vite is still emitted explicitly as nil — an absent :vite
            means {:mode :build} to zodiac-assets, which would run npm and vite
            once per instance"
    (let [config (#'instance/instance-config {:master-secret master} valid-instance-opts)]
      (is (contains? (:sepal.app.server/zodiac-assets config) :vite))
      (is (nil? (get-in config [:sepal.app.server/zodiac-assets :vite])))
      (is (not (contains? config :sepal.app.server/zodiac-hot-reload)))
      (is (false? (get-in config [:sepal.app.server/zodiac :reload-per-request?]))))))

(deftest test-dev-opts-reach-the-config-when-set
  (testing "the three REPL-only knobs land where zodiac expects them"
    (let [vite {:mode :dev-server
                :config-file "vite.config.dev.js"
                :package-json-dir "bases/app"}
          hot-reload {:watch-paths ["bases/app/src"]
                      :watch-extensions #{".clj" ".cljc" ".edn" ".html"}}
          config (#'instance/instance-config {:master-secret master}
                                             (assoc valid-instance-opts
                                                    :vite vite
                                                    :hot-reload hot-reload
                                                    :reload-per-request? true))]
      (is (= vite (get-in config [:sepal.app.server/zodiac-assets :vite])))
      (is (= hot-reload (:sepal.app.server/zodiac-hot-reload config)))
      (is (true? (get-in config [:sepal.app.server/zodiac :reload-per-request?])))
      (is (some #{(ig/ref :sepal.app.server/zodiac-hot-reload)}
                (get-in config [:sepal.app.server/zodiac :extensions]))
          "the hot-reload key must be wired into zodiac's extensions or it does nothing"))))

(deftest test-dev-opts-are-schema-checked
  (testing "a typo in a dev opt fails at start! rather than being ignored"
    (is (not (m/validate instance/InstanceOpts
                         (assoc valid-instance-opts :reload-per-reqest? true))))))

(deftest test-a-supplied-mail-client-is-used-as-is
  (testing "a caller may bring its own mail client, so tests reach the same
            start-process! the dispatcher calls instead of reaching around it"
    (let [mail (->RecordingMailClient (atom []))
          process (instance/start-process! {:master-secret master :mail mail})]
      (try
        (is (identical? mail (:mail process)))
        (finally
          (instance/stop-process! process))))))

(deftest test-a-supplied-mail-client-wins-over-smtp
  (testing "passing both is a caller error worth resolving predictably rather
            than building two clients"
    (let [mail (->RecordingMailClient (atom []))
          process (instance/start-process! {:master-secret master
                                            :mail mail
                                            :smtp {:host "smtp.example.org"}})]
      (try
        (is (identical? mail (:mail process)))
        (finally
          (instance/stop-process! process))))))

(deftest test-usage
  (with-two-gardens
    (fn [_process a b]
      (testing "an empty garden reports zeros, not nils"
        (is (= {:accessions 0 :materials 0 :users 0 :media-bytes 0}
               (instance/usage a))))

      (testing "the map is closed — no key appears without this test changing"
        (is (= #{:accessions :materials :users :media-bytes}
               (set (keys (instance/usage a))))))

      (instance/create-admin-user! a {:email "admin@a.example.com"
                                      :password "a-password"})

      (testing "the admin user is counted"
        (is (= 1 (:users (instance/usage a)))))

      (testing "counting one garden never sees another's rows"
        (is (= 0 (:users (instance/usage b))))))))

(deftest test-inviting-an-owner-with-no-mail-client-configured
  (let [dir (fs/create-temp-dir {:prefix "sepal-invite-nomail"})
        db-path (str (fs/path dir "sepal.db"))
        process (instance/start-process!
                  {:log-level "WARN"
                   :master-secret master
                   :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})]
    (try
      (instance/provision! {:db-path db-path})
      (let [garden (instance/start! process {:slug "nomail"
                                             :db-path db-path
                                             :app-domain "nomail.localhost"
                                             :media-key-prefix "nomail/"
                                             :media-cache-dir (str (fs/path dir "cache"))
                                             :backup-dir (str (fs/path dir "backups"))})]
        (try
          (let [thrown (try
                         (instance/invite-owner! garden {:email "owner@example.com"})
                         nil
                         (catch clojure.lang.ExceptionInfo e e))]
            (testing "it refuses, naming the reason"
              (is (some? thrown) "invite-owner! should have thrown")
              (is (= :mail-not-configured (:reason (ex-data thrown)))))

            (testing "and no owner row is left behind, so a retry can succeed"
              ;; Nothing was created, because the check happens before the user is.
              (is (= 0 (:users (instance/usage garden))))))
          (finally
            (instance/stop! garden))))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-setup-is-completed-even-when-the-invitation-cannot-be-sent
  (let [dir (fs/create-temp-dir {:prefix "sepal-invite-refused"})
        db-path (str (fs/path dir "sepal.db"))
        broken? (atom true)
        sent (atom [])
        ;; Refuses while broken, then works — a provider with a wrong hostname
        ;; that gets corrected, which is what happened in production.
        refusing (reify mail.p/MailClient
                   (send-message [_ message]
                     (if @broken?
                       (throw (ex-info "Couldn't connect to host" {}))
                       (do (swap! sent conj message) {:status :sent}))))
        process (instance/start-process!
                  {:log-level "WARN"
                   :master-secret master
                   :mail refusing
                   :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})]
    (try
      (instance/provision! {:db-path db-path})
      (let [garden (instance/start! process {:slug "refused"
                                             :db-path db-path
                                             :app-domain "refused.localhost"
                                             :media-key-prefix "refused/"
                                             :media-cache-dir (str (fs/path dir "cache"))
                                             :backup-dir (str (fs/path dir "backups"))})]
        (try
          (testing "the failure propagates, so the caller records which step broke"
            (is (thrown? clojure.lang.ExceptionInfo
                         (instance/invite-owner! garden {:email "owner@example.com"}))))

          (testing "but the wizard is still skipped"
            ;; Found in production: a mail provider with a wrong hostname left a
            ;; fully provisioned garden showing a configuration wizard its owner
            ;; cannot act on. Whether the email arrived has nothing to do with
            ;; whether the garden is configured.
            (let [response (:response (-> (peri/session (instance/handler garden))
                                          (peri/request "/login")))]
              (is (= 200 (:status response)))))

          (testing "and the invitation can be sent again once mail works"
            (reset! broken? false)
            (is (some? (:accept-url (instance/invite-owner! garden {:email "owner@example.com"}))))
            (is (= 1 (count @sent)) "one message, on the retry")
            (is (= "owner@example.com" (:to (first @sent)))))

          (testing "with one owner, not two"
            (is (= 1 (:users (instance/usage garden)))))
          (finally
            (instance/stop! garden))))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))

(deftest test-the-invitation-sender-is-the-one-configured-address
  (let [sent (atom [])
        mail (->RecordingMailClient sent)
        dir (fs/create-temp-dir {:prefix "sepal-invite-from"})
        db-path (str (fs/path dir "sepal.db"))
        process (instance/start-process!
                  {:log-level "WARN"
                   :master-secret master
                   :mail mail
                   :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})]
    (try
      (instance/provision! {:db-path db-path})
      (let [garden (instance/start! process {:slug "harlem"
                                             :db-path db-path
                                             :app-domain "harlem.localhost"
                                             :media-key-prefix "harlem/"
                                             :media-cache-dir (str (fs/path dir "cache"))
                                             :backup-dir (str (fs/path dir "backups"))})]
        (try
          (instance/invite-owner! garden {:email "owner@example.com"})

          (testing "one message went out, to the address invited"
            (is (= 1 (count @sent)))
            (is (= "owner@example.com" (:to (first @sent)))))

          (testing "from the single configured address, not one derived per garden"
            ;; SPF does not inherit to subdomains, so a per-garden sender would
            ;; need a record per garden and would fragment sending reputation —
            ;; and this is the one piece of mail that has to arrive.
            (is (= "noreply@sepal.app" (:from (first @sent)))))

          (testing "and the body links to this garden's own domain"
            (is (re-find #"https://harlem\.localhost/" (:body (first @sent)))))

          (testing "with no dangling inviter, since a managed garden has none"
            ;; The in-app template says "{inviter-name} ({inviter-email}) has
            ;; invited you"; rendered with nils that reads " () has invited you".
            (is (not (re-find #"\(\) has invited" (:body (first @sent))))))
          (finally
            (instance/stop! garden))))
      (finally
        (instance/stop-process! process)
        (fs/delete-tree dir)))))