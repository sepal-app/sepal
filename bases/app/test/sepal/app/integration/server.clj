(ns sepal.app.integration.server
  "Server lifecycle management for integration tests"
  (:require [integrant.core :as ig]
            [sepal.app.server]  ;; Load Integrant methods
            [sepal.config.interface :as config.i])
  (:import [java.io File]))

(defn- create-test-config []
  (let [db-path (.getAbsolutePath (File/createTempFile "sepal-integration-test" ".db"))
        schema-dump-file (or (System/getenv "SCHEMA_DUMP_FILE") "db/schema.sql")]
    {:sepal.app.server/zodiac-sql
     {:spec (assoc (config.i/read-config "database/config.edn" {:profile :test})
                   :jdbcUrl (str "jdbc:sqlite:" db-path))
      :context-key :db}

     :sepal.app.server/zodiac-assets
     {:build? false
      :manifest-path (or (System/getenv "ASSET_MANIFEST_PATH")
                         "app/build/.vite/manifest.json")
      :asset-resource-path (or (System/getenv "ASSET_RESOURCE_PATH")
                               "app/build/assets")
      :package-json-dir "bases/app"}

     :sepal.app.server/zodiac
     {:extensions [(ig/ref :sepal.app.server/zodiac-sql)
                   (ig/ref :sepal.app.server/zodiac-assets)]
      :request-context {:forgot-password-email-from "support@sepal.app"
                        :forgot-password-email-subject "Sepal - Reset Password"
                        :reset-password-secret "1234"
                        :app-domain "localhost:3000"}
      :cookie-secret "1234567890123456"
      :port 3000
      :start-server? true}  ;; START THE SERVER for integration tests

     :sepal.database.interface/schema
     {:database-path db-path
      :schema-dump-file schema-dump-file}

     :sepal.malli.interface/init {}}))

(defn- wait-for-server-ready
  "Wait for server to be ready by polling the health endpoint"
  [max-attempts]
  (loop [attempts 0]
    (if (>= attempts max-attempts)
      (throw (Exception. "Server failed to start within timeout"))
      (let [ready? (try
                     (slurp "http://localhost:3000/ok")
                     true
                     (catch Exception _e
                       false))]
        (if ready?
          (println "Server is ready after" attempts "attempts")
          (do
            (Thread/sleep 100)
            (recur (inc attempts))))))))

(defn start-server!
  "Start web server and return system map"
  []
  (let [config (create-test-config)
        system (ig/init config)]
    ;; Wait for server to be ready before returning
    (wait-for-server-ready 50)  ;; 50 attempts * 100ms = 5 seconds max
    system))

(defn stop-server!
  "Stop web server"
  [system]
  (ig/halt! system))

(defn with-server
  "Fixture to start/stop server around tests"
  [test-fn]
  (let [system (start-server!)]
    (try
      (test-fn system)
      (finally
        (stop-server! system)))))
