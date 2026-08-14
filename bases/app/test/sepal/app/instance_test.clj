(ns sepal.app.instance-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [peridot.core :as peri]
            [sepal.app.instance :as instance]
            [sepal.database.interface :as db.i]
            [sepal.media-transform.interface :as media-transform.i]
            [sepal.test.interface :as test.i]))

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
        (is (some? thrown) (str "slug " (pr-str slug) " must be rejected"))))))

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

(defn- with-two-gardens
  "Provision two gardens in one temp dir, start a process and both instances,
  call (f process instance-a instance-b), then tear everything down."
  [f]
  (let [dir (fs/create-temp-dir {:prefix "sepal-instances"})
        process (instance/start-process!
                  {:log-level "WARN"
                   :master-secret "master-secret-for-tests"
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
