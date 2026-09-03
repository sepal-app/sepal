(ns sepal.app.routes.setup.shared
  "Shared setup wizard functionality."
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hato.client :as http]
            [next.jdbc :as jdbc]
            [ring.core.protocols :as ring.protocols]
            [sepal.config.interface :as config.i]
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

;; These read the request context rather than the environment. In a hosted
;; process the environment belongs to no particular garden, so an env read gives
;; every garden the same answer — and the control plane passes these as opts
;; rather than as variables, so the variables are not set at all.

(defn- check-smtp-configured [{:keys [mail]}]
  (if mail
    {:status :ok :message "SMTP is configured"}
    {:status :warning
     :message "Email not configured. Password reset, user invitations, and backup notifications will not work."}))

(defn- check-s3-configured [{:keys [s3-client media-upload-bucket]}]
  (if (and s3-client (not (str/blank? media-upload-bucket)))
    {:status :ok :message "Media storage (S3) is configured"}
    {:status :warning
     :message "Media storage not configured. Cannot upload images or documents to records."}))

(defn- check-app-domain [{:keys [app-domain]}]
  (if-not (str/blank? app-domain)
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
  "Run server configuration checks and return results. Takes the zodiac request
  context, which carries the collaborators this instance was actually given."
  [{:keys [db] :as context}]
  {:smtp (check-smtp-configured context)
   :s3 (check-s3-configured context)
   :app-domain (check-app-domain context)
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

(defn copy-counting!
  "Copy in→out, calling (on-bytes done) after each chunk. Returns the byte count.

  io/copy cannot report progress, which is the only reason this exists. The
  buffer is 64 KiB rather than the 8 KiB sha256-hex uses because these are
  35 MB and 127 MB files and the callback runs once per chunk."
  [in out on-bytes]
  (let [buf (byte-array 65536)]
    (loop [done 0]
      (let [n (.read ^java.io.InputStream in buf)]
        (if (pos? n)
          (do (.write ^java.io.OutputStream out buf 0 n)
              (let [done (+ done n)]
                (on-bytes done)
                (recur done)))
          done)))))

(defn download-file!
  "Download `url` to `dest`, reporting progress as the bytes arrive.

  `on-bytes` is called with {:bytes-done :bytes-total :approximate?} after every
  chunk. :bytes-total is the response's Content-Length when the server sent one
  and `size-mb` converted to bytes when it did not — in which case
  :approximate? is true, because the manifest's size_mb comes from `du -m`
  rounded up.

  Verifies `sha256` when one is given, deleting the file and throwing on a
  mismatch. Returns `dest`."
  [url dest {:keys [size-mb sha256 on-bytes]}]
  (let [response (http/get url {:http-client http-client
                                :as :stream
                                :timeout 120000})]
    (when-not (= 200 (:status response))
      (throw (ex-info "Download failed"
                      {:status (:status response) :url url})))
    (let [declared (some-> (get-in response [:headers "content-length"]) parse-long)
          bytes-total (or declared (some-> size-mb (* 1024 1024)))
          approximate? (and (nil? declared) (some? bytes-total))
          report (fn [done]
                   (when on-bytes
                     (on-bytes {:bytes-done done
                                :bytes-total bytes-total
                                :approximate? approximate?})))]
      (report 0)
      (with-open [in (:body response)
                  out (io/output-stream (io/file dest))]
        (copy-counting! in out report))
      (when sha256
        (let [actual (sha256-hex dest)]
          (when-not (= sha256 actual)
            (io/delete-file dest true)
            (throw (ex-info "Checksum verification failed"
                            {:expected sha256 :actual actual})))))
      dest)))

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

;; The import job
;;
;; The downloads are 35 MB and ~127 MB, so they cannot run on the request
;; thread: nothing can report progress from inside a request that has not
;; returned. run-import! runs on a background thread and writes everything it
;; knows into an atom, which the SSE endpoint reads.

(def initial-job-state
  "The tracker before anything has been started."
  {:phase :idle})

(def default-synonym-ref-filename
  "The name sepal.app.main/env-opts looks for under the data home."
  "sepal-synonyms.db")

(defn default-synonym-ref-path
  "Where to put the reference file when the process was given no path.

  env-opts resolves :wfo-synonym-ref-path only when the file already exists, so
  on a first run the instance has no path and this is the destination the pool
  will look at after a restart."
  ([] (default-synonym-ref-path (System/getenv)))
  ([env] (str (fs/path (config.i/data-home env) default-synonym-ref-filename))))

(defn- failure-message
  "A message a person reading the wizard can act on."
  [^Exception e]
  (condp instance? e
    java.net.ConnectException
    "Could not connect to GitHub. Please check your network connection and try again."

    java.net.SocketTimeoutException
    "Download timed out. Please try again."

    java.net.UnknownHostException
    "Could not resolve GitHub. Please check your network connection and try again."

    (or (not-empty (.getMessage e))
        (.getName (class e)))))

(defn- progress-reporter
  "Writes a download-phase byte count into the tracker, leaving :phase alone."
  [tracker]
  (fn [progress]
    (swap! tracker merge (select-keys progress [:bytes-done :bytes-total :approximate?]))))

(defn- download-taxa!
  "Download the init database to a temp file. Returns its path."
  [tracker download-fn version]
  (let [temp (File/createTempFile "sepal-init-" ".db")]
    (swap! tracker merge {:phase :downloading-taxa
                          :bytes-done 0
                          :bytes-total nil
                          :approximate? false})
    (try
      (download-fn (:url version)
                   (.getAbsolutePath temp)
                   {:size-mb (:size_mb version)
                    :sha256 (:sha256 version)
                    :on-bytes (progress-reporter tracker)})
      (.getAbsolutePath temp)
      (catch Exception e
        (delete-temp-file (.getAbsolutePath temp))
        (throw e)))))

(defn install-synonym-reference!
  "Move a downloaded reference file into place by an atomic rename.

  The reference pool opens the file with immutable=1, which tells SQLite the
  bytes never change, so overwriting it in place while a pool is open is
  undefined behaviour. The download therefore lands beside the destination — the
  same filesystem, so ATOMIC_MOVE is available — and only then takes its name.
  A replacement is picked up on the next process start, which is fine here
  because setup runs before the pool has ever been opened."
  [tmp dest]
  (when-let [parent (fs/parent dest)]
    (fs/create-dirs parent))
  (fs/move tmp dest {:replace-existing true :atomic-move true})
  (str dest))

(defn- download-synonyms!
  "Download and install the synonym reference. Throws on failure; the caller
  decides that a reference failure is a warning rather than a failed setup."
  [tracker download-fn synonyms dest]
  (when (str/blank? (:sha256 synonyms))
    ;; A truncated 127 MB SQLite file is a working database with rows missing,
    ;; so an unverifiable reference degrades silently. Refuse it instead.
    (throw (ex-info "The synonym reference has no checksum, so it cannot be verified."
                    {:reason :missing-sha256})))
  (let [tmp (str dest ".part")]
    (swap! tracker merge {:phase :downloading-synonyms
                          :bytes-done 0
                          :bytes-total nil
                          :approximate? false})
    (when-let [parent (fs/parent dest)]
      (fs/create-dirs parent))
    (try
      (download-fn (:url synonyms)
                   tmp
                   {:size-mb (:size_mb synonyms)
                    :sha256 (:sha256 synonyms)
                    :on-bytes (progress-reporter tracker)})
      (install-synonym-reference! tmp dest)
      (catch Exception e
        (delete-temp-file tmp)
        (throw e)))))

(defn run-import!
  "The whole taxonomy import, start to finish, writing progress into `tracker`.

  Takes `fetch-manifest-fn`, `download-fn` and `import-fn` so a test can drive
  the phase sequence with no network and no 127 MB file. Never throws: a failure
  lands in the tracker as {:phase :failed :error …}, because this runs on a
  thread nobody joins and an uncaught exception would leave the tracker sitting
  in a running phase forever, showing a bar that never moves.

  The taxonomy download is required; the synonym reference is not. Taxa are what
  a managed garden cannot get later on its own, so a failure there is :failed
  and the wizard offers a retry. Synonymy is an enhancement, so a failure there
  reaches :done with a warning recorded."
  [db tracker {:keys [fetch-manifest-fn download-fn import-fn ref-dest]}]
  (try
    (swap! tracker merge {:phase :fetching-manifest})
    (let [version (select-compatible-version (fetch-manifest-fn))]
      (when-not version
        (throw (ex-info "No compatible WFO Plant List version found. Please update Sepal."
                        {:reason :no-compatible-version})))
      (let [wfo-version (get version (keyword "wfo_plant_list.version"))
            synonyms (:synonyms version)]
        ;; `synonyms` is absent — not null — on the published sepal-init-v1
        ;; entry, which means "no reference available", not an error.
        (swap! tracker merge {:wfo-version wfo-version
                              :synonyms? (some? synonyms)})
        (let [taxa-db (download-taxa! tracker download-fn version)]
          (try
            (swap! tracker merge {:phase :importing-taxa
                                  :bytes-done nil
                                  :bytes-total nil
                                  :approximate? false})
            (let [taxa-count (import-fn db taxa-db)]
              (settings.i/set-value! db "setup.wfo_plant_list_version" wfo-version)
              (swap! tracker merge {:taxa-count taxa-count}))
            (finally
              (delete-temp-file taxa-db))))
        (let [warning (when synonyms
                        (try
                          (download-synonyms! tracker download-fn synonyms ref-dest)
                          nil
                          (catch Exception e
                            (str "The taxonomy imported, but the synonym reference did not download: "
                                 (failure-message e)))))]
          (swap! tracker merge (cond-> {:phase :done
                                        :bytes-done nil
                                        :bytes-total nil
                                        :approximate? false}
                                 warning (assoc :warning warning))))))
    (catch Exception e
      (swap! tracker merge {:phase :failed :error (failure-message e)}))))

(def default-import-opts
  "The real collaborators. Tests pass their own."
  {:fetch-manifest-fn fetch-manifest
   :download-fn download-file!
   :import-fn import-from-init-db!})

(def ^:private startable-phases
  "Phases a start request may leave. :failed is here so the wizard's retry
  button works; a run in flight is not restartable."
  #{:idle :failed})

(defn start-import!
  "Start the import on a background thread if it is not already running.
  Returns :started or :already-running.

  The compare-and-set! is the guard: a double form submit, or an impatient
  reload, must not start two 127 MB downloads."
  [db tracker opts]
  (let [current @tracker]
    (if (and (contains? startable-phases (:phase current))
             (compare-and-set! tracker current {:phase :fetching-manifest}))
      (do (future (run-import! db tracker (merge default-import-opts opts)))
          :started)
      :already-running)))

;; The progress stream

(defn job-frame
  "The tracker state as the browser sees it: camelCase keys, a percentage
  already worked out, and no Clojure keywords."
  [{:keys [phase bytes-done bytes-total approximate? wfo-version taxa-count
           synonyms? error warning]}]
  {"phase" (name (or phase :idle))
   "bytesDone" bytes-done
   "bytesTotal" bytes-total
   "percent" (when (and bytes-done bytes-total (pos? bytes-total))
               (min 100 (long (/ (* 100 bytes-done) bytes-total))))
   "approximate" (boolean approximate?)
   "wfoVersion" wfo-version
   "taxaCount" taxa-count
   "synonyms" (boolean synonyms?)
   "error" error
   "warning" warning})

(def terminal-phases #{:done :failed})

(defn sse-body
  "A response body that writes one SSE frame per tick until the job is over.

  The .flush is load-bearing: without it the writer buffers and the browser sees
  nothing until the stream closes, which is exactly the dead-button behaviour
  this replaces. The terminal-phase check is equally load-bearing: without it
  the connection lives for the life of the process."
  [tracker & {:keys [poll-ms] :or {poll-ms 500}}]
  (reify ring.protocols/StreamableResponseBody
    (write-body-to-stream [_ _ output-stream]
      (with-open [w (io/writer output-stream)]
        (loop []
          (let [state @tracker]
            (.write w (str "data: " (json/write-str (job-frame state)) "\n\n"))
            (.flush w)
            (when-not (contains? terminal-phases (:phase state))
              (Thread/sleep ^long poll-ms)
              (recur))))))))
