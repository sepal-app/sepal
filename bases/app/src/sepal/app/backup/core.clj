(ns sepal.app.backup.core
  "Core backup functionality for Sepal database."
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [sepal.app.ui.datetime :as datetime]
            [sepal.database.interface :as db.i]
            [sepal.mail.interface :as mail.i]
            [sepal.scheduler.interface :as scheduler.i]
            [sepal.settings.interface :as settings.i]
            [sepal.user.interface :as user.i])
  (:import [java.io File]
           [java.nio.file Files]
           [java.security MessageDigest]
           [java.time Instant LocalTime ZonedDateTime ZoneId]
           [java.time.format DateTimeFormatter]
           [java.time.temporal ChronoUnit]
           [java.util.zip CRC32 ZipEntry ZipOutputStream]))

;; -----------------------------------------------------------------------------
;; Configuration

(def ^:private setting-keys
  {:frequency "backup.frequency"
   :last-run-at "backup.last_run_at"})

(defn- get-data-home
  "Get Sepal data home directory."
  []
  (or (System/getenv "SEPAL_DATA_HOME")
      (when-let [xdg (System/getenv "XDG_DATA_HOME")]
        (str (fs/path xdg "Sepal")))
      (if (= "Mac OS X" (System/getProperty "os.name"))
        (str (fs/path (System/getProperty "user.home") "Library" "Application Support" "Sepal"))
        (str (fs/path (fs/xdg-data-home) "Sepal")))))

(defn get-backup-path
  "Get the backup directory path from env var or default."
  []
  (or (System/getenv "BACKUP_PATH")
      (str (fs/path (get-data-home) "backups"))))

(defn get-config
  "Get backup configuration from settings."
  [db]
  (let [settings (settings.i/get-values db "backup")]
    {:frequency (some-> (get settings (:frequency setting-keys))
                        keyword)
     :path (get-backup-path)
     :last-run-at (some-> (get settings (:last-run-at setting-keys))
                          Instant/parse)}))

(defn set-config!
  "Save backup configuration to settings."
  [db config]
  (let [settings (cond-> {}
                   (:frequency config)
                   (assoc (:frequency setting-keys) (name (:frequency config)))

                   (contains? config :last-run-at)
                   (assoc (:last-run-at setting-keys)
                          (some-> (:last-run-at config) str)))]
    (settings.i/set-values! db settings)))

(defn ensure-backup-dir!
  "Ensure the backup directory exists. Creates it if necessary.
   Returns {:valid? true :path path} or {:valid? false :error \"message\"}."
  []
  (let [path (get-backup-path)
        dir (io/file path)]
    (try
      (when-not (.exists dir)
        (.mkdirs dir))
      (cond
        (not (.exists dir))
        {:valid? false :error (str "Could not create backup directory: " path)}

        (not (.isDirectory dir))
        {:valid? false :error (str "Backup path is not a directory: " path)}

        (not (.canWrite dir))
        {:valid? false :error (str "Backup directory is not writable: " path)}

        :else
        {:valid? true :path path})
      (catch Exception e
        {:valid? false :error (str "Error accessing backup directory: " (.getMessage e))}))))

;; -----------------------------------------------------------------------------
;; Backup creation

(defn- sha256-hex
  "Calculate SHA-256 hash of byte array, return as hex string."
  [^bytes data]
  (let [digest (MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest data)]
    (apply str (map #(format "%02x" (bit-and % 0xff)) hash-bytes))))

(defn- crc32
  "Calculate CRC32 of byte array."
  ^long [^bytes data]
  (let [crc (CRC32.)]
    (.update crc data)
    (.getValue crc)))

(defn- write-zip-entry!
  "Write a single entry to a ZipOutputStream using STORED (no compression)."
  [^ZipOutputStream zos ^String entry-name ^bytes data]
  (let [entry (doto (ZipEntry. entry-name)
                (.setMethod ZipEntry/STORED)
                (.setSize (alength data))
                (.setCompressedSize (alength data))
                (.setCrc (crc32 data)))]
    (.putNextEntry zos entry)
    (.write zos data)
    (.closeEntry zos)))

(defn- format-timestamp
  "Format an Instant as a filesystem-safe timestamp string."
  [^Instant instant]
  (let [formatter (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HHmmss")
                      (.withZone (ZoneId/systemDefault)))]
    (.format formatter instant)))

(defn- get-schema-version
  "Get the current database schema version."
  [db]
  (let [result (db.i/execute-one! db ["SELECT version FROM schema_version ORDER BY applied_at DESC LIMIT 1"])]
    ;; Column might be namespaced or plain depending on JDBC options
    (or (:version result)
        (:schema_version/version result)
        "unknown")))

(defn- get-sepal-version
  "Get the current Sepal application version."
  []
  ;; TODO: Read from a version file or build info
  "0.1.0")

(defn- vacuum-to-bytes
  "Create a consistent backup of the database and return as byte array.
   Uses VACUUM INTO to create a snapshot, then reads the file."
  [db backup-path]
  (let [temp-file (File/createTempFile "sepal-backup-" ".db" (io/file backup-path))]
    (try
      ;; VACUUM INTO creates a consistent snapshot
      (jdbc/execute! db [(str "VACUUM INTO '" (.getAbsolutePath temp-file) "'")])
      (Files/readAllBytes (.toPath temp-file))
      (finally
        (.delete temp-file)))))

(defn create-backup!
  "Create a timestamped backup ZIP file.

   Returns {:filename \"...\" :size-bytes N :created-at #inst} on success,
   or throws an exception on failure."
  [db backup-path]
  (let [now (Instant/now)
        timestamp (format-timestamp now)
        backup-name (str "sepal-backup-" timestamp)
        archive-file (io/file backup-path (str backup-name ".zip"))

        ;; Create database snapshot
        db-bytes (vacuum-to-bytes db backup-path)
        db-hash (sha256-hex db-bytes)

        ;; Build metadata
        metadata {:version (get-sepal-version)
                  :schema_version (get-schema-version db)
                  :created_at (str now)
                  :database {:filename "sepal.db"
                             :size_bytes (alength db-bytes)
                             :sha256 db-hash}}
        metadata-bytes (.getBytes (json/write-str metadata) "UTF-8")]

    ;; Create ZIP archive
    (with-open [zos (ZipOutputStream. (io/output-stream archive-file))]
      (write-zip-entry! zos (str backup-name "/metadata.json") metadata-bytes)
      (write-zip-entry! zos (str backup-name "/sepal.db") db-bytes))

    (log/info "Created backup" (.getName archive-file)
              "size:" (.length archive-file) "bytes")

    {:filename (.getName archive-file)
     :size-bytes (.length archive-file)
     :created-at now}))

;; -----------------------------------------------------------------------------
;; Backup listing and retrieval

(defn- parse-backup-filename
  "Parse a backup filename to extract the timestamp.
   Returns nil if filename doesn't match expected pattern."
  [filename]
  (when-let [[_ timestamp] (re-matches #"sepal-backup-(\d{4}-\d{2}-\d{2}T\d{6})\.zip" filename)]
    (let [formatter (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HHmmss")
                        (.withZone (ZoneId/systemDefault)))]
      (try
        (Instant/from (.parse formatter timestamp))
        (catch Exception _
          nil)))))

(defn list-backups
  "List backup files in the backup directory.
   Returns a sequence of maps sorted by created-at descending."
  [backup-path & {:keys [limit] :or {limit 5}}]
  (let [dir (io/file backup-path)]
    (if (and (.exists dir) (.isDirectory dir))
      (->> (.listFiles dir)
           (filter #(.isFile ^File %))
           (filter #(str/ends-with? (.getName ^File %) ".zip"))
           (keep (fn [^File f]
                   (when-let [created-at (parse-backup-filename (.getName f))]
                     {:filename (.getName f)
                      :size-bytes (.length f)
                      :created-at created-at})))
           (sort-by :created-at #(compare %2 %1))
           (take limit))
      [])))

(defn get-backup-file
  "Get a File object for a backup by filename.
   Returns nil if file doesn't exist or filename is invalid."
  [backup-path filename]
  ;; Validate filename pattern to prevent directory traversal
  (when (and (string? filename)
             (re-matches #"sepal-backup-\d{4}-\d{2}-\d{2}T\d{6}\.zip" filename))
    (let [file (io/file backup-path filename)]
      (when (and (.exists file) (.isFile file))
        file))))

;; -----------------------------------------------------------------------------
;; Email notifications

(defn- get-admin-emails
  "Get email addresses of all admin users."
  [db]
  (->> (user.i/get-by-role db "admin")
       (map :user/email)
       (filter some?)))

(defn- format-datetime-for-email
  "Format an instant for display in email using org timezone."
  [db instant]
  (let [timezone (datetime/get-timezone db)]
    (datetime/format-for-email instant timezone)))

(defn- send-backup-success-email!
  "Send backup success notification to all admin users."
  [mail db app-domain backup-result]
  (let [admin-emails (get-admin-emails db)
        download-url (str "https://" app-domain "/settings/backups/"
                          (:filename backup-result) "/download")
        subject "Sepal Backup Completed Successfully"
        body (str "A database backup was completed successfully.\n\n"
                  "Filename: " (:filename backup-result) "\n"
                  "Size: " (format "%.2f MB" (/ (:size-bytes backup-result) 1048576.0)) "\n"
                  "Created: " (format-datetime-for-email db (:created-at backup-result)) "\n\n"
                  "Download: " download-url "\n")]
    (doseq [email admin-emails]
      (try
        (mail.i/send-message mail {:to email
                                   :subject subject
                                   :body body})
        (catch Exception e
          (log/error e "Failed to send backup success email to" email))))))

(defn- send-backup-failure-email!
  "Send backup failure notification to all admin users."
  [mail db error-message]
  (let [admin-emails (get-admin-emails db)
        subject "Sepal Backup Failed"
        body (str "A scheduled database backup has failed.\n\n"
                  "Error: " error-message "\n\n"
                  "Please check the server logs for more details.")]
    (doseq [email admin-emails]
      (try
        (mail.i/send-message mail {:to email
                                   :subject subject
                                   :body body})
        (catch Exception e
          (log/error e "Failed to send backup failure email to" email))))))

;; -----------------------------------------------------------------------------
;; Scheduler integration

(defn- backup-schedule
  "Generate a Chime schedule sequence for the given frequency."
  [frequency]
  (let [now (ZonedDateTime/now)
        ;; Find next 2:00 AM
        today-2am (-> now
                      (.with (LocalTime/of 2 0 0))
                      (.truncatedTo ChronoUnit/MINUTES))
        next-2am (if (.isAfter now today-2am)
                   (.plusDays today-2am 1)
                   today-2am)]
    (case frequency
      :daily (iterate #(.plusDays ^ZonedDateTime % 1) next-2am)
      :weekly (let [;; Find next Sunday at 2:00 AM
                    days-until-sunday (mod (- 7 (.getValue (.getDayOfWeek next-2am))) 7)
                    next-sunday (if (zero? days-until-sunday)
                                  next-2am
                                  (.plusDays next-2am days-until-sunday))]
                (iterate #(.plusWeeks ^ZonedDateTime % 1) next-sunday))
      :monthly (let [;; Find next 1st of month at 2:00 AM
                     next-first (-> next-2am
                                    (.withDayOfMonth 1))
                     next-first (if (.isBefore next-first now)
                                  (.plusMonths next-first 1)
                                  next-first)]
                 (iterate #(.plusMonths ^ZonedDateTime % 1) next-first))
      nil)))

(defn get-next-backup-time
  "Calculate the next scheduled backup time for the given frequency.
   Returns an Instant, or nil if frequency is :disabled or nil."
  [frequency]
  (when-let [schedule (backup-schedule frequency)]
    (.toInstant ^ZonedDateTime (first schedule))))

(defn- backup-task
  "Create a backup task function for the scheduler."
  [db mail app-domain]
  (fn [_scheduled-time]
    (let [dir-check (ensure-backup-dir!)]
      (if-not (:valid? dir-check)
        (do
          (log/error "Backup directory check failed:" (:error dir-check))
          (send-backup-failure-email! mail db (:error dir-check)))
        (try
          (let [result (create-backup! db (:path dir-check))]
            (set-config! db {:last-run-at (Instant/now)})
            (send-backup-success-email! mail db app-domain result))
          (catch Exception e
            (log/error e "Scheduled backup failed")
            (send-backup-failure-email! mail db (.getMessage e))))))))

(defn register-backup-job!
  "Register the backup job with the scheduler based on current config.
   Called on app startup and when config changes."
  [scheduler db mail app-domain]
  (let [config (get-config db)]
    (if (not= :disabled (:frequency config))
      (do
        (log/info "Registering backup job with frequency:" (:frequency config))
        (scheduler.i/schedule! scheduler
                               :backup
                               (map #(.toInstant ^ZonedDateTime %)
                                    (backup-schedule (:frequency config)))
                               (backup-task db mail app-domain)))
      (do
        (log/info "Backup disabled, cancelling any existing job")
        (scheduler.i/cancel! scheduler :backup)))))

;; -----------------------------------------------------------------------------
;; Integrant lifecycle

(defmethod ig/init-key :sepal.app.backup/job [_ {:keys [scheduler zodiac mail app-domain]}]
  (let [db (get zodiac :zodiac.ext.sql/db)]
    (register-backup-job! scheduler db mail app-domain)
    {:scheduler scheduler :db db :mail mail :app-domain app-domain}))

(defmethod ig/halt-key! :sepal.app.backup/job [_ {:keys [scheduler]}]
  (scheduler.i/cancel! scheduler :backup))
