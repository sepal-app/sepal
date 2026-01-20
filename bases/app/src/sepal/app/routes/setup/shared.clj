(ns sepal.app.routes.setup.shared
  "Shared setup wizard functionality."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hato.client :as http]
            [next.jdbc :as jdbc]
            [sepal.database.interface :as db.i]
            [sepal.settings.interface :as settings.i])
  (:import [java.io File]
           [java.security MessageDigest]
           [java.time Instant]))

(defn setup-complete?
  "Returns true if the setup wizard has been completed."
  [db]
  (some? (settings.i/get-value db "setup.completed_at")))

(defn complete-setup!
  "Mark setup as complete. Sets the completed_at timestamp."
  [db]
  (settings.i/set-value! db "setup.completed_at" (str (Instant/now))))

(defn reset-setup!
  "Reset setup status (for testing). Clears completed_at and current_step."
  [db]
  (settings.i/delete! db "setup.completed_at")
  (settings.i/delete! db "setup.current_step"))

(defn admin-exists?
  "Returns true if at least one admin user exists."
  [db]
  (db.i/exists? db {:select [1]
                    :from :user
                    :where [:= :role "admin"]}))

(defn get-current-step
  "Get the current step the user is on (persisted for resume)."
  [db]
  (or (some-> (settings.i/get-value db "setup.current_step")
              parse-long)
      1))

(defn set-current-step!
  "Save the current step for resume capability."
  [db step]
  (settings.i/set-value! db "setup.current_step" (str step)))

;; Server configuration checks

(defn- check-env-vars
  "Check if environment variables are set (non-empty)."
  [& var-names]
  (every? #(not (str/blank? (System/getenv %))) var-names))

(defn- check-smtp-configured []
  (if (check-env-vars "SMTP_HOST" "SMTP_USERNAME" "SMTP_PASSWORD")
    {:status :ok :message "SMTP is configured"}
    {:status :warning
     :message "Email not configured. Password reset, user invitations, and backup notifications will not work."}))

(defn- check-s3-configured []
  (if (check-env-vars "AWS_ACCESS_KEY_ID" "AWS_SECRET_ACCESS_KEY" "MEDIA_UPLOAD_BUCKET")
    {:status :ok :message "Media storage (S3) is configured"}
    {:status :warning
     :message "Media storage not configured. Cannot upload images or documents to records."}))

(defn- check-app-domain []
  (if (check-env-vars "APP_DOMAIN")
    {:status :ok :message "App domain is configured"}
    {:status :warning
     :message "App domain not set. Links in emails will be incorrect."}))

(defn- check-spatialite
  "Check if SpatiaLite extension is available."
  [db]
  (try
    (db.i/execute-one! db {:select [[[:spatialite_version] :version]]})
    {:status :ok :message "SpatiaLite is available for geo-coordinates"}
    (catch Exception _
      {:status :warning
       :message "SpatiaLite not available. Geo-coordinates for collections will not work."})))

(defn check-server-config
  "Run server configuration checks and return results."
  [db]
  {:smtp (check-smtp-configured)
   :s3 (check-s3-configured)
   :app-domain (check-app-domain)
   :spatialite (check-spatialite db)})

;; WFO Taxonomy Import

(def manifest-url
  "URL to fetch the init database manifest from GitHub Releases."
  "https://github.com/sepal-app/sepal/releases/latest/download/sepal-init-manifest.json")

(def supported-init-schemas
  "Set of init database schema versions this version of Sepal can import."
  #{1})

;; HTTP client that follows redirects (needed for GitHub releases)
(def http-client
  (http/build-http-client {:redirect-policy :always}))

(defn can-import-wfo?
  "Returns true if WFO import is available (no existing taxa)."
  [db]
  (zero? (db.i/count db {:select [:id] :from [:taxon]})))

(defn fetch-manifest
  "Fetch the init database manifest from GitHub Releases.
   Returns the parsed manifest or throws on error."
  []
  (let [response (http/get manifest-url {:http-client http-client
                                         :as :string
                                         :timeout 30000})]
    (if (= 200 (:status response))
      (json/read-str (:body response) :key-fn keyword)
      (throw (ex-info "Failed to fetch manifest"
                      {:status (:status response)})))))

(defn select-compatible-version
  "Select the newest compatible version from the manifest.
   Returns the version map or nil if none compatible."
  [manifest]
  (->> (:versions manifest)
       (filter #(contains? supported-init-schemas (:schema_version %)))
       first))

(defn- sha256-hex
  "Compute SHA256 hash of a file and return as hex string."
  [file-path]
  (let [digest (MessageDigest/getInstance "SHA-256")
        buffer (byte-array 8192)]
    (with-open [in (io/input-stream file-path)]
      (loop []
        (let [n (.read in buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur)))))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(defn download-init-db
  "Download the init database to a temp file and verify checksum.
   Returns the temp file path."
  [url expected-sha256]
  (let [temp-file (File/createTempFile "sepal-init-" ".db")
        response (http/get url {:http-client http-client
                                :as :stream
                                :timeout 120000})]
    (if (= 200 (:status response))
      (do
        (with-open [in (:body response)
                    out (io/output-stream temp-file)]
          (io/copy in out))
        ;; Verify checksum if provided
        (when expected-sha256
          (let [actual-sha256 (sha256-hex temp-file)]
            (when (not= expected-sha256 actual-sha256)
              (.delete temp-file)
              (throw (ex-info "Checksum verification failed"
                              {:expected expected-sha256
                               :actual actual-sha256})))))
        (.getAbsolutePath temp-file))
      (throw (ex-info "Failed to download init database"
                      {:status (:status response)
                       :url url})))))

(defn import-from-init-db!
  "Import taxa from the init database into Sepal.
   Uses a transaction for the INSERT to ensure all-or-nothing import.
   Returns the number of taxa imported."
  [db init-db-path]
  ;; ATTACH must be outside transaction in SQLite
  (jdbc/execute! db [(str "ATTACH DATABASE '" init-db-path "' AS init")])
  (try
    ;; Use transaction for the INSERT
    (jdbc/with-transaction [tx db]
      ;; Copy all taxa in one INSERT
      (jdbc/execute! tx ["INSERT INTO taxon (id, wfo_taxon_id, name, author, rank, parent_id)
                          SELECT id, wfo_taxon_id, name, author, rank, parent_id
                          FROM init.taxon"])
      ;; Return count
      (-> (jdbc/execute-one! tx ["SELECT COUNT(*) as count FROM taxon"])
          :count))
    (finally
      (jdbc/execute! db ["DETACH DATABASE init"]))))

(defn delete-temp-file
  "Delete a temp file, ignoring errors."
  [path]
  (try
    (io/delete-file path true)
    (catch Exception _)))

(defn get-init-db-info
  "Fetch manifest and return info about the available init database.
   Returns map with :wfo-version, :size-mb, :available? or :error."
  []
  (try
    (let [manifest (fetch-manifest)]
      (if-let [version (select-compatible-version manifest)]
        {:available? true
         :wfo-version (get version (keyword "wfo_plant_list.version"))
         :size-mb (:size_mb version)}
        {:available? false
         :error "No compatible version available"}))
    (catch Exception e
      {:available? false
       :error (.getMessage e)})))

(defn import-wfo-taxonomy!
  "Import WFO Plant List taxonomy data from GitHub Releases.
   Downloads the init database, imports taxa, and sets the version setting."
  [db]
  (if-not (can-import-wfo? db)
    {:error "Cannot import WFO: taxa already exist in database"}
    (try
      ;; Fetch manifest
      (let [manifest (fetch-manifest)]
        (if-let [version (select-compatible-version manifest)]
          ;; Download and import
          (let [temp-file (download-init-db (:url version) (:sha256 version))]
            (try
              (let [taxa-count (import-from-init-db! db temp-file)
                    wfo-version (get version (keyword "wfo_plant_list.version"))]
                ;; Set version setting after successful import
                (settings.i/set-value! db "setup.wfo_plant_list_version" wfo-version)
                {:ok true
                 :taxa-count taxa-count
                 :wfo-version wfo-version
                 :message (format "Imported %,d taxa from WFO Plant List %s"
                                  taxa-count
                                  wfo-version)})
              (finally
                (delete-temp-file temp-file))))
          ;; No compatible version
          {:error "No compatible WFO Plant List version found. Please update Sepal."}))

      (catch java.net.ConnectException _
        {:error "Could not connect to GitHub. Please check your network connection and try again."})

      (catch java.net.SocketTimeoutException _
        {:error "Download timed out. Please try again."})

      (catch Exception e
        {:error (str "Import failed: " (.getMessage e))}))))
