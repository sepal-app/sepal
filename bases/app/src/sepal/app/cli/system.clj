(ns sepal.app.cli.system
  "Minimal Integrant system for CLI operations.

   Similar to sepal.app.test.system but without the web server."
  (:require [babashka.fs :as fs]
            [integrant.core :as ig]
            [lambdaisland.uri :as uri]
            [next.jdbc.connection :as connection]
            [sepal.database.interface :as db.i]
            [sepal.malli.interface :as malli.i])
  (:import [com.zaxxer.hikari HikariDataSource]))

;; =============================================================================
;; Integrant keys
;; =============================================================================

(defn- build-jdbc-url
  "Build a SQLite JDBC URL with pragma query parameters."
  [db-path pragmas]
  (let [base-url (format "jdbc:sqlite:%s" db-path)]
    (if (seq pragmas)
      (format "%s?%s" base-url (uri/map->query-string pragmas))
      base-url)))

(defmethod ig/init-key ::datasource [_ {:keys [database-path pragmas]}]
  (when-not (fs/exists? database-path)
    (throw (ex-info (format "Database not found at: %s" database-path)
                    {:database-path database-path})))
  (db.i/init)
  (let [jdbc-url (build-jdbc-url database-path pragmas)]
    (connection/->pool HikariDataSource {:jdbcUrl jdbc-url
                                         :maximumPoolSize 2})))

(defmethod ig/halt-key! ::datasource [_ ds]
  (.close ^HikariDataSource ds))

;; =============================================================================
;; System configuration
;; =============================================================================

(defn- get-database-path
  "Get database path from env var or default XDG location."
  []
  (or (System/getenv "DATABASE_PATH")
      (str (fs/path (fs/xdg-data-home) "Sepal" "sepal.db"))))

(defn system-config
  "Create CLI system configuration.

   Includes:
   - Malli initialization (for schema decode/encode transformers)
   - Database connection pool"
  []
  (let [db-path (get-database-path)]
    {:sepal.malli.interface/init {}
     ::datasource {:database-path db-path
                   :pragmas {:journal_mode "WAL"
                             :foreign_keys "ON"
                             :busy_timeout "5000"}}}))

;; =============================================================================
;; System lifecycle
;; =============================================================================

(defn start-system
  "Start the CLI system and return it."
  []
  (let [config (system-config)]
    (ig/init config)))

(defn stop-system
  "Stop the CLI system."
  [system]
  (ig/halt! system))

(defn get-db
  "Get the database connection from a running system."
  [system]
  (::datasource system))

(defn with-system*
  "Execute f with a started CLI system.

   Handles system startup errors gracefully and returns exit codes."
  [f]
  (try
    (let [system (start-system)]
      (try
        (f system)
        (finally
          (stop-system system))))
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [database-path]} (ex-data e)]
        (if database-path
          (do (println (format "Error: %s" (ex-message e)))
              (println "Set DATABASE_PATH environment variable to specify the database location.")
              1)
          (throw e))))
    (catch Exception e
      (println (format "Error: %s" (ex-message e)))
      1)))

(defmacro with-system
  "Execute body with a started CLI system.

   The system is bound to the provided symbol.

   Example:
     (with-system [sys]
       (let [db (get-db sys)]
         (user.i/get-all db)))"
  [[sym] & body]
  `(with-system* (fn [~sym] ~@body)))
