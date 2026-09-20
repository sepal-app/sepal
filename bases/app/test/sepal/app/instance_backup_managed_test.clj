(ns sepal.app.instance-backup-managed-test
  "The scenario backup! exists for: a garden whose store, not its own job,
  decides when backups run, so the instance's scheduled job registers nothing
  and this is the only thing that produces a backup for it."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.instance :as instance]
            [sepal.app.test.system :refer [*garden* injected-backup-store-system-fixture]]))

(use-fixtures :once injected-backup-store-system-fixture)

(deftest test-backup-produces-a-backup-when-the-store-owns-the-schedule
  (testing "one call is enough, because there is no job here to do it instead"
    (let [result (instance/backup! *garden*)]
      (try
        (is (re-matches #"sepal-backup-\d{4}-\d{2}-\d{2}T\d{6}\.zip" (:filename result)))
        (is (pos? (:size-bytes result)))
        (is (some? (:created-at result)))
        (finally
          ;; FakeBackupStore's put-backup hands create-fn java.io.tmpdir, not a
          ;; directory this fixture owns, so the fixture's own teardown never
          ;; sees this file. Delete it here or it outlives the test on whoever
          ;; runs the suite.
          (fs/delete-if-exists (fs/path (System/getProperty "java.io.tmpdir") (:filename result))))))))
