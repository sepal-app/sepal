(ns sepal.app.routes.setup.shared
  "Shared setup wizard functionality."
  (:require [clojure.string :as str]
            [sepal.database.interface :as db.i]
            [sepal.settings.interface :as settings.i])
  (:import [java.time Instant]))

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

(defn can-import-wfo?
  "Returns true if WFO import is available (no existing taxa)."
  [db]
  (zero? (db.i/count db {:select [:id] :from [:taxon]})))

(defn import-wfo-taxonomy!
  "Import WFO taxonomy data.
   
   TODO: Implement actual download from Zenodo and import.
   For now, this is a stub that simulates the import."
  [db]
  (if-not (can-import-wfo? db)
    {:error "Cannot import WFO: taxa already exist in database"}
    ;; TODO: Implement actual download and import
    ;; For now, just mark as imported without actually importing
    (do
      (settings.i/set-values! db
                              {"setup.wfo_imported" "true"
                               "setup.wfo_import_version" "2025-06"})
      {:ok true
       :message "WFO import simulated (not yet implemented)"})))
