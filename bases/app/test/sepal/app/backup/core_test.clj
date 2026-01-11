(ns sepal.app.backup.core-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.backup.core :as backup]
            [sepal.app.test.system :refer [*db* *mail-client* default-system-fixture]]
            [sepal.settings.interface :as settings.i]
            [sepal.user.interface :as user.i])
  (:import [java.nio.file Files]
           [java.time Instant]
           [java.util.zip ZipFile]))

(use-fixtures :once default-system-fixture)

(defn- create-admin-user! [db]
  (let [email (str "admin-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email
                        :password "testpassword"
                        :role :admin
                        :status :active})
    email))

(defn- sent-messages []
  @(:sent-messages *mail-client*))

(defn- clear-sent-messages! []
  (reset! (:sent-messages *mail-client*) []))

(deftest test-get-set-config
  (testing "get-config returns nil frequency when not set"
    (let [config (backup/get-config *db*)]
      (is (nil? (:frequency config)))
      (is (string? (:path config))) ; path comes from env/default, always present
      (is (nil? (:last-run-at config)))))

  (testing "set-config! and get-config round-trip"
    (backup/set-config! *db* {:frequency :daily})
    (let [config (backup/get-config *db*)]
      (is (= :daily (:frequency config))))

    ;; Clean up
    (settings.i/set-values! *db* {"backup.frequency" nil})))

(deftest test-get-backup-path
  (testing "returns a path string"
    (is (string? (backup/get-backup-path)))
    (is (pos? (count (backup/get-backup-path))))))

(deftest test-ensure-backup-dir
  (testing "creates directory if it doesn't exist"
    (let [temp-parent (Files/createTempDirectory "backup-parent" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-parent "/test-backups")]
      (try
        ;; Directory doesn't exist yet
        (is (not (.exists (io/file backup-path))))

        ;; Use with-redefs to test ensure-backup-dir! with our temp path
        (with-redefs [backup/get-backup-path (constantly backup-path)]
          (let [result (backup/ensure-backup-dir!)]
            (is (:valid? result))
            (is (= backup-path (:path result)))
            (is (.exists (io/file backup-path)))
            (is (.isDirectory (io/file backup-path)))))
        (finally
          ;; Clean up
          (when (.exists (io/file backup-path))
            (.delete (io/file backup-path)))
          (Files/delete temp-parent))))))

(deftest test-create-backup
  (testing "creates a valid backup ZIP file"
    (let [temp-dir (Files/createTempDirectory "backup-test" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-dir)]
      (try
        (let [result (backup/create-backup! *db* backup-path)]
          (is (string? (:filename result)))
          (is (re-matches #"sepal-backup-\d{4}-\d{2}-\d{2}T\d{6}\.zip" (:filename result)))
          (is (pos-int? (:size-bytes result)))
          (is (instance? Instant (:created-at result)))

          ;; Verify ZIP contents
          (let [zip-file (io/file backup-path (:filename result))]
            (is (.exists zip-file))
            (with-open [zf (ZipFile. zip-file)]
              (let [entries (enumeration-seq (.entries zf))
                    entry-names (set (map #(.getName %) entries))]
                ;; Should have metadata.json and sepal.db in a folder
                (is (= 2 (count entries)))
                (is (some #(re-matches #"sepal-backup-.*/metadata\.json" %) entry-names))
                (is (some #(re-matches #"sepal-backup-.*/sepal\.db" %) entry-names))

                ;; Verify metadata content
                (let [metadata-entry (->> entries
                                          (filter #(re-matches #".*metadata\.json" (.getName %)))
                                          first)]
                  (with-open [input-stream (.getInputStream zf metadata-entry)]
                    (let [metadata (json/read (io/reader input-stream) :key-fn keyword)]
                      (is (string? (:version metadata)))
                      (is (string? (:schema_version metadata)))
                      (is (string? (:created_at metadata)))
                      (is (= "sepal.db" (get-in metadata [:database :filename])))
                      (is (pos-int? (get-in metadata [:database :size_bytes])))
                      (is (= 64 (count (get-in metadata [:database :sha256])))))))))))
        (finally
          ;; Clean up
          (doseq [f (file-seq (io/file backup-path))]
            (when (.isFile f) (.delete f)))
          (Files/delete temp-dir))))))

(deftest test-list-backups
  (testing "lists backup files sorted by date descending"
    (let [temp-dir (Files/createTempDirectory "backup-test" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-dir)]
      (try
        ;; Create a few backups
        (let [b1 (backup/create-backup! *db* backup-path)
              _ (Thread/sleep 1100) ; Ensure different timestamps
              b2 (backup/create-backup! *db* backup-path)
              backups (backup/list-backups backup-path)]
          (is (= 2 (count backups)))
          ;; Most recent first
          (is (= (:filename b2) (:filename (first backups))))
          (is (= (:filename b1) (:filename (second backups)))))
        (finally
          ;; Clean up
          (doseq [f (file-seq (io/file backup-path))]
            (when (.isFile f) (.delete f)))
          (Files/delete temp-dir))))))

(deftest test-list-backups-with-limit
  (testing "limits number of backups returned"
    (let [temp-dir (Files/createTempDirectory "backup-test" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-dir)]
      (try
        ;; Create 3 backups
        (dotimes [_ 3]
          (backup/create-backup! *db* backup-path)
          (Thread/sleep 1100))
        (is (= 2 (count (backup/list-backups backup-path :limit 2))))
        (is (= 1 (count (backup/list-backups backup-path :limit 1))))
        (finally
          ;; Clean up
          (doseq [f (file-seq (io/file backup-path))]
            (when (.isFile f) (.delete f)))
          (Files/delete temp-dir))))))

(deftest test-get-backup-file
  (testing "returns file for valid backup"
    (let [temp-dir (Files/createTempDirectory "backup-test" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-dir)]
      (try
        (let [result (backup/create-backup! *db* backup-path)
              file (backup/get-backup-file backup-path (:filename result))]
          (is (some? file))
          (is (.exists file))
          (is (= (:filename result) (.getName file))))
        (finally
          (doseq [f (file-seq (io/file backup-path))]
            (when (.isFile f) (.delete f)))
          (Files/delete temp-dir)))))

  (testing "returns nil for invalid filename patterns"
    (is (nil? (backup/get-backup-file "/tmp" "../etc/passwd")))
    (is (nil? (backup/get-backup-file "/tmp" "malicious.zip")))
    (is (nil? (backup/get-backup-file "/tmp" "sepal-backup-invalid.zip")))))

;; -----------------------------------------------------------------------------
;; Email notification tests

(deftest test-backup-success-email
  (testing "sends success email to admin users after backup"
    (let [admin-email (create-admin-user! *db*)
          temp-dir (Files/createTempDirectory "backup-test" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-dir)
          app-domain "test.sepal.app"]
      (try
        (clear-sent-messages!)
        (let [result (backup/create-backup! *db* backup-path)]
          (#'backup/send-backup-success-email! *mail-client* *db* app-domain result)

          ;; Verify emails were sent to admin users
          (let [messages (sent-messages)
                recipients (set (map :to messages))]
            (is (pos? (count messages)) "Should send at least one email")
            (is (contains? recipients admin-email) "Should send to the admin user")
            ;; Check content of one of the messages
            (let [msg (first (filter #(= admin-email (:to %)) messages))]
              (is (= "Sepal Backup Completed Successfully" (:subject msg)))
              (is (str/includes? (:body msg) (:filename result)))
              (is (str/includes? (:body msg) app-domain))
              (is (str/includes? (:body msg) "Download:")))))
        (finally
          (doseq [f (file-seq (io/file backup-path))]
            (when (.isFile f) (.delete f)))
          (Files/delete temp-dir))))))

(deftest test-backup-failure-email
  (testing "sends failure email to admin users on backup error"
    (let [admin-email (create-admin-user! *db*)
          error-message "Test backup failure"]
      (clear-sent-messages!)
      (#'backup/send-backup-failure-email! *mail-client* *db* error-message)

      ;; Verify emails were sent
      (let [messages (sent-messages)
            recipients (set (map :to messages))]
        (is (pos? (count messages)) "Should send at least one email")
        (is (contains? recipients admin-email) "Should send to the admin user")
        ;; Check content
        (let [msg (first (filter #(= admin-email (:to %)) messages))]
          (is (= "Sepal Backup Failed" (:subject msg)))
          (is (str/includes? (:body msg) error-message))
          (is (str/includes? (:body msg) "check the server logs")))))))

(deftest test-backup-emails-sent-to-multiple-admins
  (testing "sends emails to multiple admin users"
    (let [admin1 (create-admin-user! *db*)
          admin2 (create-admin-user! *db*)
          error-message "Test error"]
      (clear-sent-messages!)
      (#'backup/send-backup-failure-email! *mail-client* *db* error-message)

      ;; Should have sent to both new admins
      (let [messages (sent-messages)
            recipients (set (map :to messages))]
        (is (>= (count messages) 2) "Should send to at least 2 admins")
        (is (contains? recipients admin1) "Should send to admin1")
        (is (contains? recipients admin2) "Should send to admin2")))))

(deftest test-get-next-backup-time
  (testing "returns nil for disabled frequency"
    (is (nil? (backup/get-next-backup-time :disabled)))
    (is (nil? (backup/get-next-backup-time nil))))

  (testing "returns an Instant for valid frequencies"
    (is (instance? Instant (backup/get-next-backup-time :daily)))
    (is (instance? Instant (backup/get-next-backup-time :weekly)))
    (is (instance? Instant (backup/get-next-backup-time :monthly))))

  (testing "next backup time is in the future"
    (let [now (Instant/now)]
      (is (.isAfter (backup/get-next-backup-time :daily) now))
      (is (.isAfter (backup/get-next-backup-time :weekly) now))
      (is (.isAfter (backup/get-next-backup-time :monthly) now)))))
