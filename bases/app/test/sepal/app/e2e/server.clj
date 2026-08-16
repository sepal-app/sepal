(ns sepal.app.e2e.server
  "Server lifecycle management for e2e tests.

  Built from the same instance API production uses, so the browser-driven suite
  exercises the real startup path rather than a config maintained only here."
  (:require [babashka.fs :as fs]
            [sepal.app.instance :as instance]
            [sepal.app.routes.setup.shared :as setup.shared]
            [zodiac.ext.sql :as z.sql])
  (:import [java.net ServerSocket]))

(defn- find-available-port
  "Find an available port by letting the OS assign one"
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn- wait-for-server-ready
  "Wait for server to be ready by polling the health endpoint"
  [port max-attempts]
  (loop [attempts 0]
    (if (>= attempts max-attempts)
      (throw (Exception. (str "Server failed to start on port " port " within timeout")))
      (let [ready? (try
                     (slurp (str "http://localhost:" port "/ok"))
                     true
                     (catch Exception _e
                       false))]
        (if ready?
          (println "Server is ready on port" port "after" attempts "attempts")
          (do
            (Thread/sleep 100)
            (recur (inc attempts))))))))

(defn db
  "The database connection for a started server."
  [started]
  (get-in started [:garden :system :sepal.app.server/zodiac ::z.sql/db]))

(defn start-server!
  "Start web server on a random available port and return a started value."
  []
  (let [port (find-available-port)
        dir (fs/create-temp-dir {:prefix "sepal-e2e"})
        db-path (str (fs/path dir "sepal.db"))
        backup-dir (str (fs/path dir "backups"))
        process (instance/start-process!
                  {:master-secret "1234567890123456"
                   :extensions-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")})]
    (fs/create-dirs backup-dir)
    (instance/provision! {:db-path db-path})
    (when (not= (instance/schema-version {:db-path db-path})
                (instance/latest-schema-version))
      (instance/migrate! {:db-path db-path}))
    (let [garden (instance/start! process
                                  {:slug "e2e"
                                   :db-path db-path
                                   :app-domain (str "localhost:" port)
                                   :media-key-prefix "media/"
                                   :media-cache-dir (str (fs/path dir "cache"))
                                   :backup-dir backup-dir
                                   :start-server? true
                                   :jetty-port port})
          started {:dir dir :process process :garden garden :port port}]
      ;; Mark setup as complete so e2e tests bypass the setup wizard
      (setup.shared/complete-setup! (db started))
      ;; Wait for server to be ready before returning
      (wait-for-server-ready port 50) ;; 50 attempts * 100ms = 5 seconds max
      started)))

(defn server-url
  "Get the base URL for the running server"
  [started]
  (str "http://localhost:" (:port started)))

(defn stop-server!
  "Stop web server"
  [started]
  (instance/stop! (:garden started))
  (instance/stop-process! (:process started))
  (fs/delete-tree (:dir started)))

(defn with-server
  "Fixture to start/stop server around tests"
  [test-fn]
  (let [started (start-server!)]
    (try
      (test-fn started)
      (finally
        (stop-server! started)))))
