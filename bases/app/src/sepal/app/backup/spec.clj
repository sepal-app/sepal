(ns sepal.app.backup.spec
  "Malli schemas for backup configuration and metadata.")

(def Frequency
  "Backup frequency. nil means disabled."
  [:enum :daily :weekly :monthly])

(def Config
  [:map
   [:frequency {:optional true} [:maybe Frequency]]
   [:path :string]
   [:last-run-at {:optional true} [:maybe inst?]]])

(def BackupMetadata
  [:map
   [:version :string]
   [:schema_version :string]
   [:created_at :string]
   [:database [:map
               [:filename [:= "sepal.db"]]
               [:size_bytes pos-int?]
               [:sha256 [:string {:min 64 :max 64}]]]]])

(def BackupFile
  [:map
   [:filename :string]
   [:size-bytes pos-int?]
   [:created-at inst?]])

(def DiskUsage
  [:map
   [:total-bytes pos-int?]
   [:used-bytes nat-int?]
   [:free-bytes nat-int?]
   [:backup-bytes nat-int?]])
