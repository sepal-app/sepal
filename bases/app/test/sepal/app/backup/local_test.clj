(ns sepal.app.backup.local-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [sepal.app.backup.local :as local]
            [sepal.app.backup.protocols :as backup.p]
            [sepal.app.routes.settings.routes :as settings.routes]
            [zodiac.core :as z]))

(defn- with-dir [f]
  (let [dir (fs/create-temp-dir {:prefix "sepal-local-store"})]
    (try (f (str dir))
         (finally (fs/delete-tree dir)))))

(deftest test-put-backup-writes-into-the-backup-directory
  (testing "the create fn is handed the store's own directory, so nothing moves"
    (with-dir
      (fn [dir]
        (let [store (local/->LocalBackupStore dir)
              seen (atom nil)
              result (backup.p/put-backup
                       store
                       (fn [d]
                         (reset! seen d)
                         (spit (fs/file d "sepal-backup-2026-01-01T020000.zip") "z")
                         {:filename "sepal-backup-2026-01-01T020000.zip"
                          :size-bytes 1
                          :created-at (java.time.Instant/parse "2026-01-01T02:00:00Z")}))]
          (is (= dir @seen) "the store chooses the directory")
          (is (= "sepal-backup-2026-01-01T020000.zip" (:filename result))
              "and returns the create fn's result unchanged")
          (is (fs/exists? (fs/path dir "sepal-backup-2026-01-01T020000.zip"))
              "the zip stays where a self-hoster's backup has always been"))))))

(deftest test-list-backups-reads-the-directory
  (testing "newest first, and only files that parse as backups"
    (with-dir
      (fn [dir]
        (spit (fs/file dir "sepal-backup-2026-01-01T020000.zip") "a")
        (spit (fs/file dir "sepal-backup-2026-01-02T020000.zip") "bb")
        (spit (fs/file dir "notes.txt") "ignored")
        (let [rows (backup.p/list-backups (local/->LocalBackupStore dir))]
          (is (= ["sepal-backup-2026-01-02T020000.zip"
                  "sepal-backup-2026-01-01T020000.zip"]
                 (mapv :filename rows)))
          (is (= [2 1] (mapv :size-bytes rows))))))))

(deftest test-list-backups-is-not-capped
  (testing "the directory is the window; the store adds no limit of its own"
    (with-dir
      (fn [dir]
        (doseq [d (range 1 13)]
          (spit (fs/file dir (format "sepal-backup-2026-01-%02dT020000.zip" d)) "x"))
        (is (= 12 (count (backup.p/list-backups (local/->LocalBackupStore dir)))))))))

(deftest test-download-url-is-the-apps-own-route
  (testing "a self-hoster downloads through the app, not through a signed URL"
    (with-dir
      (fn [dir]
        ;; z/url-for reads the router bound to the current request, and a plain
        ;; unit test has none. Stubbing it out proves the store asks the app's
        ;; own backup-download route for the filename, without standing up a
        ;; real router just to read back the path it would build.
        (with-redefs [z/url-for (fn [route-name params] [route-name params])]
          (is (= [settings.routes/backup-download
                  {:filename "sepal-backup-2026-01-01T020000.zip"}]
                 (backup.p/download-url (local/->LocalBackupStore dir)
                                        "sepal-backup-2026-01-01T020000.zip"))))))))

(deftest test-local-store-does-not-manage-the-schedule
  (testing "the frequency setting and the backup job stay in charge"
    (with-dir
      (fn [dir]
        (is (false? (backup.p/manages-schedule? (local/->LocalBackupStore dir))))))))

(deftest test-two-stores-stay-separate
  (testing "two backup directories stay separate, with no environment involved"
    (with-dir
      (fn [a]
        (with-dir
          (fn [b]
            (spit (fs/file a "sepal-backup-2026-01-01T020000.zip") "a")
            (spit (fs/file b "sepal-backup-2026-01-02T020000.zip") "b")
            (is (= ["sepal-backup-2026-01-01T020000.zip"]
                   (mapv :filename (backup.p/list-backups (local/->LocalBackupStore a)))))
            (is (= ["sepal-backup-2026-01-02T020000.zip"]
                   (mapv :filename (backup.p/list-backups (local/->LocalBackupStore b)))))))))))
