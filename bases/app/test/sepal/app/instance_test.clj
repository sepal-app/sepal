(ns sepal.app.instance-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [next.jdbc :as jdbc]
            [peridot.core :as peri]
            [sepal.app.instance :as instance]
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

(defn- schema-file []
  (or (System/getenv "SCHEMA_DUMP_FILE")
      (throw (ex-info "SCHEMA_DUMP_FILE is not set — run inside the devenv shell" {}))))

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
               (instance/provision! {:db-path db-path :schema-file (schema-file)})))
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
                       (instance/provision! {:db-path db-path :schema-file (schema-file)})
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "provision! should have thrown")
          (is (= :database-exists (:reason (ex-data thrown)))))
        (is (= "not a database" (slurp db-path)))
        (finally
          (fs/delete-tree dir)))))

  (testing "a missing schema file is refused"
    (let [dir (fs/create-temp-dir {:prefix "sepal-provision"})
          db-path (str (fs/path dir "sepal.db"))]
      (try
        (let [thrown (try
                       (instance/provision! {:db-path db-path
                                             :schema-file (str (fs/path dir "nope.sql"))})
                       nil
                       (catch clojure.lang.ExceptionInfo e e))]
          (is (some? thrown) "provision! should have thrown")
          (is (= :schema-file-missing (:reason (ex-data thrown)))))
        (is (not (fs/exists? db-path)))
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
                   :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")
                   :media-cache-dir (str (fs/path dir "cache"))})
        start (fn [slug]
                (let [db-path (str (fs/path dir slug "sepal.db"))]
                  (instance/provision! {:db-path db-path :schema-file (schema-file)})
                  (instance/start! process {:slug slug
                                            :db-path db-path
                                            :app-domain (str slug ".localhost")})))
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
                                               :app-domain "ghost.localhost"})
                     nil
                     (catch clojure.lang.ExceptionInfo e e))]
        (is (some? thrown) "start! should have thrown")
        (is (= :database-missing (:reason (ex-data thrown)))))
      (finally
        (instance/stop-process! process)
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
