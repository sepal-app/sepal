(ns sepal.app.backup.fake-store
  "A BackupStore for tests: no directory, no network, and a switch for the one
  failure mode the page has to render differently from an empty list."
  (:require [sepal.app.backup.protocols :as backup.p]))

(deftype FakeBackupStore [rows manages-schedule? fail?]
  backup.p/BackupStore

  (put-backup [_ create-fn]
    (when fail? (throw (ex-info "store unreachable" {:type ::unreachable})))
    (create-fn (System/getProperty "java.io.tmpdir")))

  (list-backups [_]
    (when fail? (throw (ex-info "store unreachable" {:type ::unreachable})))
    rows)

  (download-url [_ filename]
    (str "https://backups.example/" filename))

  (manages-schedule? [_] manages-schedule?))
