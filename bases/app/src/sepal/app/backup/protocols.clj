(ns sepal.app.backup.protocols
  "Where a garden's backups are written to, listed from and downloaded through.

  A caller that operates backups elsewhere — a control plane, a self-hoster's own
  offsite copy — injects its own implementation at start!, the way it may inject
  its own mail client. The app ships one implementation, over a local directory,
  and that is the one a self-hosted install runs.

  An implementation is built for one garden with that garden's location closed
  over. Nothing here takes a prefix, a bucket or a path, so no code reachable
  from a request can address another garden's backups even in principle.")

(defprotocol BackupStore
  (put-backup [this create-fn]
    "Store one new backup.

    create-fn takes a directory and returns {:filename :size-bytes :created-at}
    for a zip it wrote there. The store chooses the directory: a local store
    hands over the directory it lists from, so the zip is already where it
    belongs; a remote store hands over scratch space, uploads what appears
    there, and removes it.

    Returns create-fn's result. Throws if the backup was created but could not
    be stored — a caller that catches this has a backup it cannot trust.")

  (list-backups [this]
    "The backups this garden can browse, newest first, as
    [{:filename :size-bytes :created-at}]. An empty sequence means there are
    none. Throws if the store cannot be reached: a caller must be able to tell
    those apart, because rendering 'no backups yet' for an unreachable store is
    a lie about the customer's data.")

  (download-url [this filename]
    "Where the browser fetches this backup. May be a path on this app or an
    absolute URL to somewhere else.")

  (manages-schedule? [this]
    "True when something outside this garden decides when backups run. The app
    then registers no backup job and offers no frequency to change, because
    changing it here would not change what actually runs."))
