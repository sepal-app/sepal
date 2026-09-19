(ns sepal.app.backup.local
  "Backups in a directory on this machine, downloaded through this app. The only
  implementation the app ships, and what every self-hosted install uses."
  (:require [sepal.app.backup.core :as backup]
            [sepal.app.backup.protocols :as backup.p]
            [sepal.app.routes.settings.routes :as settings.routes]
            [zodiac.core :as z]))

(deftype LocalBackupStore [backup-dir]
  backup.p/BackupStore

  (put-backup [_ create-fn]
    ;; The directory this store lists from, so the zip is written where it will
    ;; be served from and nothing is copied or moved afterwards.
    (create-fn backup-dir))

  (list-backups [_]
    ;; Uncapped on purpose: the directory is the window, and hiding a backup a
    ;; self-hoster still has on disk would be a change to what they own.
    (backup/list-backups backup-dir))

  (download-url [_ filename]
    (z/url-for settings.routes/backup-download {:filename filename}))

  (manages-schedule? [_] false))
