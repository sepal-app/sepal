(ns sepal.app.routes.settings.backups-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.backup.core :as backup]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.settings.interface :as settings.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [java.nio.file Files]
           [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- flash-banner-text [body]
  (some-> (.selectFirst body ".banner-text") (.text)))

(defn- create-user! [db role password]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email :password password :role role})
    email))

(deftest test-backups-page-requires-admin
  (testing "GET /settings/backups returns 403 for non-admin users"
    (let [password "testpassword123"
          editor-email (create-user! *db* :editor password)
          reader-email (create-user! *db* :reader password)]
      ;; Editor should get 403
      (let [sess (app.test/login editor-email password)
            {:keys [response]} (peri/request sess "/settings/backups")]
        (is (= 403 (:status response))))
      ;; Reader should get 403
      (let [sess (app.test/login reader-email password)
            {:keys [response]} (peri/request sess "/settings/backups")]
        (is (= 403 (:status response))))))

  (testing "GET /settings/backups returns 200 for admin users"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response]} (peri/request sess "/settings/backups")]
      (is (= 200 (:status response))))))

(deftest test-backups-page-content
  (testing "GET /settings/backups shows frequency selector and warning note"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response]} (peri/request sess "/settings/backups")
          body (Jsoup/parse ^String (:body response))]
      ;; Should have frequency select
      (is (some? (.selectFirst body "select[name=frequency]")))
      ;; Should have warning alert about media files
      (is (.contains (:body response) "Media files are stored separately")))))

(deftest test-update-backup-frequency
  (testing "POST /settings/backups updates frequency setting"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response]} (peri/request sess "/settings/backups")
          token (test.i/response-anti-forgery-token response)]
      ;; Update to daily
      (let [{:keys [response] :as sess} (peri/request sess "/settings/backups"
                                                      :request-method :post
                                                      :params {:__anti-forgery-token token
                                                               :frequency "daily"})
            _ (is (= 303 (:status response)) "Should redirect after update")
            {:keys [response]} (peri/follow-redirect sess)
            body (Jsoup/parse ^String (:body response))]
        (is (= 200 (:status response)))
        (is (= "Backup settings updated successfully" (flash-banner-text body)))
        ;; Verify setting was saved
        (is (= :daily (:frequency (backup/get-config *db*)))))

      ;; Clean up
      (settings.i/set-values! *db* {"backup.frequency" nil}))))

(deftest test-update-backup-frequency-requires-admin
  (testing "POST /settings/backups returns 403 for non-admin users"
    (let [password "testpassword123"
          editor-email (create-user! *db* :editor password)
          ;; First login as admin to get a valid token
          admin-email (create-user! *db* :admin password)
          admin-sess (app.test/login admin-email password)
          {:keys [response]} (peri/request admin-sess "/settings/backups")
          token (test.i/response-anti-forgery-token response)
          ;; Try to POST as editor
          sess (app.test/login editor-email password)
          {:keys [response]} (peri/request sess "/settings/backups"
                                           :request-method :post
                                           :params {:__anti-forgery-token token
                                                    :frequency "daily"})]
      (is (= 403 (:status response))))))

(deftest test-download-backup
  (testing "GET /settings/backups/:filename/download returns backup file"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          ;; Create a backup first
          temp-dir (Files/createTempDirectory "backup-test" (into-array java.nio.file.attribute.FileAttribute []))
          backup-path (str temp-dir)]
      (try
        (with-redefs [backup/get-backup-path (constantly backup-path)]
          (let [result (backup/create-backup! *db* backup-path)
                sess (app.test/login email password)
                {:keys [response]} (peri/request sess (str "/settings/backups/" (:filename result) "/download"))]
            (is (= 200 (:status response)))
            (is (= "application/zip" (get-in response [:headers "Content-Type"])))
            (is (.contains (get-in response [:headers "Content-Disposition"])
                           (:filename result)))))
        (finally
          ;; Clean up
          (doseq [f (file-seq (java.io.File. backup-path))]
            (when (.isFile f) (.delete f)))
          (Files/delete temp-dir))))))

(deftest test-download-backup-not-found
  (testing "GET /settings/backups/:filename/download returns 404 for non-existent file"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response]} (peri/request sess "/settings/backups/sepal-backup-2099-01-01T000000.zip/download")]
      (is (= 404 (:status response))))))

(deftest test-download-backup-invalid-filename
  (testing "GET /settings/backups/:filename/download returns 404 for invalid filename pattern"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)]
      ;; Path traversal attempt
      (let [{:keys [response]} (peri/request sess "/settings/backups/..%2F..%2Fetc%2Fpasswd/download")]
        (is (= 404 (:status response))))
      ;; Invalid filename
      (let [{:keys [response]} (peri/request sess "/settings/backups/malicious.zip/download")]
        (is (= 404 (:status response)))))))

(deftest test-download-backup-requires-admin
  (testing "GET /settings/backups/:filename/download returns 403 for non-admin"
    (let [password "testpassword123"
          editor-email (create-user! *db* :editor password)
          sess (app.test/login editor-email password)
          {:keys [response]} (peri/request sess "/settings/backups/sepal-backup-2026-01-01T020000.zip/download")]
      (is (= 403 (:status response))))))
