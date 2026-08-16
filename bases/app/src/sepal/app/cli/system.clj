(ns sepal.app.cli.system
  "Minimal Integrant system for CLI operations: the malli registry and a small
   connection pool, opened the same way every other Sepal pool is."
  (:require [babashka.fs :as fs]
            [integrant.core :as ig]
            [next.jdbc.connection :as connection]
            [sepal.config.interface :as config.i]
            [sepal.database.interface :as db.i]
            [sepal.malli.interface :as malli.i])
  (:import [com.zaxxer.hikari HikariDataSource]))

;; =============================================================================
;; Integrant keys
;; =============================================================================

(defmethod ig/init-key ::datasource [_ {:keys [db-path extension-library-path]}]
  (when-not (fs/exists? db-path)
    (throw (ex-info (format "Database not found at: %s" db-path)
                    {:database-path db-path})))
  (db.i/init)
  (connection/->pool HikariDataSource
                     (assoc (db.i/hikari-spec {:db-path db-path
                                               :extension-library-path extension-library-path})
                            :maximumPoolSize 2)))

(defmethod ig/halt-key! ::datasource [_ ds]
  (.close ^HikariDataSource ds))

;; =============================================================================
;; System configuration
;; =============================================================================

(defn system-config
  "Create CLI system configuration.

   Includes:
   - Malli initialization (for schema decode/encode transformers)
   - Database connection pool

   :db-path defaults to sepal.db under the data home."
  ([] (system-config {}))
  ([{:keys [db-path extension-library-path]}]
   {::malli.i/init {}
    ::datasource {:db-path (or db-path (str (fs/path (config.i/data-home) "sepal.db")))
                  :extension-library-path (or extension-library-path
                                              (System/getenv "EXTENSIONS_LIBRARY_PATH"))}}))

;; =============================================================================
;; System lifecycle
;; =============================================================================

(defn start-system
  "Start the CLI system and return it."
  ([] (start-system {}))
  ([opts]
   (ig/init (system-config opts))))

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
              (println "Set SEPAL_DATA_HOME environment variable to specify the data directory.")
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
