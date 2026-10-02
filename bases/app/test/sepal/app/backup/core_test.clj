(ns sepal.app.backup.core-test
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.backup.core :as backup]
            [sepal.app.backup.fake-store :as fake-store]
            [sepal.app.backup.local :as local]
            [sepal.app.test.system :refer [*db* *mail-client* default-system-fixture]]
            [sepal.i18n.interface :as i18n]
            [sepal.scheduler.interface :as scheduler.i]
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
      (is (nil? (:last-run-at config)))))

  (testing "set-config! and get-config round-trip"
    (backup/set-config! *db* {:frequency :daily})
    (let [config (backup/get-config *db*)]
      (is (= :daily (:frequency config))))

    ;; Clean up
    (settings.i/set-values! *db* {"backup.frequency" nil})))

(deftest test-backup-dir-is-explicit
  (testing "two directories stay separate, with no environment involved"
    (let [dir (fs/create-temp-dir {:prefix "sepal-backups"})
          a (str (fs/path dir "a"))
          b (str (fs/path dir "b"))]
      (try
        (is (= {:valid? true :path a} (backup/ensure-backup-dir! a)))
        (is (= {:valid? true :path b} (backup/ensure-backup-dir! b)))
        (finally
          (fs/delete-tree dir))))))

(deftest test-ensure-backup-dir
  (testing "creates directory if it doesn't exist"
    (let [temp-parent (Files/createTempDirectory "backup-parent" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-parent "/test-backups")]
      (try
        ;; Directory doesn't exist yet
        (is (not (.exists (io/file backup-path))))

        (let [result (backup/ensure-backup-dir! backup-path)]
          (is (:valid? result))
          (is (= backup-path (:path result)))
          (is (.exists (io/file backup-path)))
          (is (.isDirectory (io/file backup-path))))
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
          app-base-url "https://test.sepal.app"
          email-from "backups@example.org"]
      (try
        (clear-sent-messages!)
        (let [result (backup/create-backup! *db* backup-path)]
          (#'backup/send-backup-success-email! *mail-client* email-from *db* app-base-url result)

          ;; Verify emails were sent to admin users
          (let [messages (sent-messages)
                recipients (set (map :to messages))]
            (is (pos? (count messages)) "Should send at least one email")
            (is (contains? recipients admin-email) "Should send to the admin user")
            ;; Check content of one of the messages
            (let [msg (first (filter #(= admin-email (:to %)) messages))]
              (is (= "Sepal Backup Completed Successfully" (:subject msg)))
              (is (= email-from (:from msg))
                  "the message carries a real address, not a nil that would
                   throw building it against a real SMTP client")
              (is (str/includes? (:body msg) (:filename result)))
              (is (str/includes? (:body msg) (str app-base-url "/settings/backups/")))
              (is (str/includes? (:body msg) "Download:")))))
        (finally
          (doseq [f (file-seq (io/file backup-path))]
            (when (.isFile f) (.delete f)))
          (Files/delete temp-dir))))))

(deftest test-backup-failure-email
  (testing "sends failure email to admin users on backup error"
    (let [admin-email (create-admin-user! *db*)
          error-message "Test backup failure"
          email-from "backups@example.org"]
      (clear-sent-messages!)
      (#'backup/send-backup-failure-email! *mail-client* email-from *db* error-message)

      ;; Verify emails were sent
      (let [messages (sent-messages)
            recipients (set (map :to messages))]
        (is (pos? (count messages)) "Should send at least one email")
        (is (contains? recipients admin-email) "Should send to the admin user")
        ;; Check content
        (let [msg (first (filter #(= admin-email (:to %)) messages))]
          (is (= "Sepal Backup Failed" (:subject msg)))
          (is (= email-from (:from msg)))
          (is (str/includes? (:body msg) error-message))
          (is (str/includes? (:body msg) "check the server logs")))))))

(deftest test-backup-emails-sent-to-multiple-admins
  (testing "sends emails to multiple admin users"
    (let [admin1 (create-admin-user! *db*)
          admin2 (create-admin-user! *db*)
          error-message "Test error"]
      (clear-sent-messages!)
      (#'backup/send-backup-failure-email! *mail-client* "backups@example.org" *db* error-message)

      ;; Should have sent to both new admins
      (let [messages (sent-messages)
            recipients (set (map :to messages))]
        (is (>= (count messages) 2) "Should send to at least 2 admins")
        (is (contains? recipients admin1) "Should send to admin1")
        (is (contains? recipients admin2) "Should send to admin2")))))

(deftest test-get-next-backup-time
  (testing "returns nil for disabled frequency"
    (is (nil? (backup/get-next-backup-time :disabled "UTC")))
    (is (nil? (backup/get-next-backup-time nil "UTC"))))

  (testing "returns an Instant for valid frequencies"
    (is (instance? Instant (backup/get-next-backup-time :daily "UTC")))
    (is (instance? Instant (backup/get-next-backup-time :weekly "UTC")))
    (is (instance? Instant (backup/get-next-backup-time :monthly "UTC"))))

  (testing "next backup time is in the future"
    (let [now (Instant/now)]
      (is (.isAfter (backup/get-next-backup-time :daily "UTC") now))
      (is (.isAfter (backup/get-next-backup-time :weekly "UTC") now))
      (is (.isAfter (backup/get-next-backup-time :monthly "UTC") now)))))

(deftest test-backups-run-at-2am-in-the-garden
  ;; Saturday 5:00 PM in Belize, UTC-6, which UTC already calls 11 PM.
  (let [now (Instant/parse "2026-10-03T23:00:00Z")]
    (testing "daily runs at 2:00 AM the garden's time"
      (is (= (Instant/parse "2026-10-04T08:00:00Z")
             (backup/get-next-backup-time :daily "America/Belize" now))))
    (testing "weekly runs on the garden's Sunday"
      (is (= (Instant/parse "2026-10-04T08:00:00Z")
             (backup/get-next-backup-time :weekly "America/Belize" now))))
    (testing "monthly runs on the garden's 1st"
      (is (= (Instant/parse "2026-11-01T08:00:00Z")
             (backup/get-next-backup-time :monthly "America/Belize" now))))))

(deftest test-a-daylight-saving-gap-moves-one-run-only
  ;; New York skips 2:00 AM on March 8, 2026, so that run is at 3:00. The next
  ;; must be back at 2:00, not stuck an hour late until the next restart.
  (let [runs (take 2 (#'backup/backup-schedule :daily "America/New_York"
                                               (Instant/parse "2026-03-07T12:00:00Z")))]
    (is (= [(Instant/parse "2026-03-08T07:00:00Z")
            (Instant/parse "2026-03-09T06:00:00Z")]
           (mapv #(.toInstant ^java.time.ZonedDateTime %) runs)))))

(deftest test-backup-names-are-in-utc
  ;; Written and read in the server's zone, a name meant different times on
  ;; different hosts, and the hour the clocks went back named two backups alike.
  ;;
  ;; This sets the JVM's default zone for its duration, which is safe only
  ;; because Kaocha runs tests one at a time.
  (let [default (java.util.TimeZone/getDefault)
        instant (Instant/parse "2026-01-01T02:00:00Z")]
    (try
      (java.util.TimeZone/setDefault (java.util.TimeZone/getTimeZone "Pacific/Kiritimati"))
      (is (= "2026-01-01T020000" (#'backup/format-timestamp instant)))
      (is (= instant (#'backup/parse-backup-filename "sepal-backup-2026-01-01T020000.zip")))
      (finally
        (java.util.TimeZone/setDefault default)))))

(deftest test-backup-task-stores-through-the-store
  (testing "the store chooses the directory and gets the result"
    (let [dir (fs/create-temp-dir {:prefix "sepal-task"})]
      (try
        (let [store (local/->LocalBackupStore (str dir))
              task (#'backup/backup-task *db* *mail-client* "backups@example.org"
                                         "https://test.sepal.app" store)]
          (task (java.time.Instant/now))
          (is (= 1 (count (backup/list-backups (str dir))))
              "one zip, in the store's directory"))
        (finally
          (settings.i/set-values! *db* {"backup.last_run_at" nil})
          (fs/delete-tree dir))))))

(deftest test-no-job-is-registered-when-the-store-manages-the-schedule
  (testing "a garden whose backups are driven from outside registers nothing"
    (let [scheduled (atom [])
          cancelled (atom [])]
      (with-redefs [scheduler.i/schedule! (fn [_ id _ _] (swap! scheduled conj id))
                    scheduler.i/cancel! (fn [_ id] (swap! cancelled conj id))]
        (backup/register-backup-job! ::scheduler *db* *mail-client* "backups@example.org"
                                     "https://test.sepal.app"
                                     (fake-store/->FakeBackupStore [] true false))
        (is (empty? @scheduled) "nothing scheduled")
        (is (empty? @cancelled)
            "and nothing cancelled either: there was never a job here to cancel")))))

(deftest test-a-local-store-still-registers-from-the-setting
  (testing "a self-hosted garden's schedule still comes from its own frequency"
    (let [scheduled (atom [])
          dir (fs/create-temp-dir {:prefix "sepal-register"})]
      (try
        (backup/set-config! *db* {:frequency :daily})
        (with-redefs [scheduler.i/schedule! (fn [_ id _ _] (swap! scheduled conj id))]
          (backup/register-backup-job! ::scheduler *db* *mail-client* "backups@example.org"
                                       "https://test.sepal.app"
                                       (local/->LocalBackupStore (str dir))))
        (is (= [:backup] @scheduled))
        (finally
          (settings.i/set-values! *db* {"backup.frequency" nil})
          (fs/delete-tree dir))))))

(deftest test-backup-email-uses-each-admins-language
  (testing "each admin reads the notice in their saved language; the rest get English"
    (let [spanish (create-admin-user! *db*)
          english (create-admin-user! *db*)]
      (user.i/update! *db* (:user/id (user.i/get-by-email *db* spanish)) {:language "es"})
      (i18n/load-catalogs! {"es" (i18n/parse-catalog "es" "msgid \"Sepal Backup Failed\"\nmsgstr \"Falló la copia de seguridad de Sepal\"\n")})
      (try
        (clear-sent-messages!)
        (#'backup/send-backup-failure-email! *mail-client* "backups@example.org" *db* "disk full")
        (let [subject-for (fn [email] (:subject (first (filter #(= email (:to %)) (sent-messages)))))]
          (is (= "Falló la copia de seguridad de Sepal" (subject-for spanish)))
          (is (= "Sepal Backup Failed" (subject-for english))))
        (finally
          (i18n/load-catalogs! {}))))))
