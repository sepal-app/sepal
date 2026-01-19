(ns sepal.app.e2e.server
  "Server lifecycle management for e2e tests"
  (:require [integrant.core :as ig]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.server] ;; Load Integrant methods
            [sepal.malli.interface] ;; Load Malli Integrant methods
            [zodiac.ext.sql :as z.sql])
  (:import [java.io File]
           [java.net ServerSocket]))

(defn- find-available-port
  "Find an available port by letting the OS assign one"
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defonce ^:dynamic *server-port* nil)

(defn- create-test-config [port]
  (let [db-path (.getAbsolutePath (File/createTempFile "sepal-e2e-test" ".db"))
        schema-dump-file (or (System/getenv "SCHEMA_DUMP_FILE") "db/schema.sql")
        extension-library-path (System/getenv "EXTENSIONS_LIBRARY_PATH")]
    {:sepal.app.server/zodiac-sql
     {:database-path db-path
      :pragmas {:journal_mode "WAL"
                :foreign_keys "ON"
                :enable_load_extension "true"}
      :extensions ["mod_spatialite"]
      :extension-library-path extension-library-path
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
                        :app-domain (str "localhost:" port)}
      :cookie-secret "1234567890123456"
      :port port
      :start-server? true}

     :sepal.database.interface/schema
     {:database-path db-path
      :schema-dump-file schema-dump-file}

     :sepal.malli.interface/init {}}))

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

(defn start-server!
  "Start web server on a random available port and return system map"
  []
  (let [port (find-available-port)
        config (create-test-config port)
        system (ig/init config)
        db (-> system :sepal.app.server/zodiac ::z.sql/db)]
    ;; Mark setup as complete so e2e tests bypass the setup wizard
    (setup.shared/complete-setup! db)
    ;; Wait for server to be ready before returning
    (wait-for-server-ready port 50) ;; 50 attempts * 100ms = 5 seconds max
    (assoc system ::port port)))

(defn stop-server!
  "Stop web server"
  [system]
  (ig/halt! system))

(defn server-url
  "Get the base URL for the running server"
  [system]
  (str "http://localhost:" (::port system)))

(defn with-server
  "Fixture to start/stop server around tests"
  [test-fn]
  (let [system (start-server!)]
    (try
      (test-fn system)
      (finally
        (stop-server! system)))))
