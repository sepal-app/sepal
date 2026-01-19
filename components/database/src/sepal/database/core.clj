(ns sepal.database.core
  (:require [camel-snake-kebab.core :as csk]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [honey.sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc.result-set])
  (:import [java.sql ResultSet ResultSetMetaData]))

(defn ->kebab-case
  "Same as csk/->kebab-case but only use '_' as the separator.

  This is needed so a column like :s3_bucket doesn't get turned into :s-3-bucket
  "
  [v]
  (when (seq v)
    (csk/->kebab-case v :separator \_)))

(defn ->snake-case
  "Same as csk/->snake-case but only use '-' as the separator.

  This is needed so a column like :s3-bucket doesn't get turned into :s_3_bucket
  "
  [v]
  (when (seq v)
    (csk/->snake_case v :separator \-)))

(defn- get-table-name
  "Copied from the private function next.jdbc.result-set/get-table-name."
  [^java.sql.ResultSetMetaData rsmeta ^Integer i]
  (try
    (.getTableName rsmeta i)
    (catch java.sql.SQLFeatureNotSupportedException _
      nil)))

(defn- label-fn
  "Convert column label to keyword, handling __ as namespace separator.
   - 'my_table__my_column' -> :my-table/my-column
   - 'my_column' (with table 'foo') -> :foo/my-column
   - 's3_bucket' -> :s3-bucket (not :s-3-bucket)
   - 'ns__col_with__more' -> :ns/col-with-more (additional __ become single _)"
  [^ResultSetMetaData rsmeta ^Integer i]
  (let [col-label (.getColumnLabel rsmeta i)
        [ns-part label-part] (str/split col-label #"__" 2)]
    (if (seq label-part)
      ;; Has __ separator: use first part as namespace
      ;; Replace any remaining __ with single _ before kebab conversion
      (keyword (->kebab-case ns-part)
               (->kebab-case (str/replace label-part #"__" "_")))
      ;; No __ separator: use table name as namespace
      (keyword (->kebab-case (get-table-name rsmeta i))
               (->kebab-case col-label)))))

(defn- column-reader
  "Read column value from result set."
  [builder ^ResultSet rs ^Integer i]
  (let [rsmeta ^ResultSetMetaData (:rsmeta builder)]
    (jdbc.result-set/read-column-by-index
      (.getObject rs i)
      rsmeta
      i)))

(defn- make-builder-fn
  "Create a result set builder that uses our label-fn for column naming.
   Returns a builder function suitable for use as :builder-fn option."
  []
  ;; We need to create a builder that:
  ;; 1. Uses our custom label-fn for column naming (handles __ separator)
  ;; 2. Uses our custom column reader for SQLite booleans
  (jdbc.result-set/builder-adapter
    ;; Base builder - we'll override its column naming via the adapter
    (fn [rs _opts]
      ;; Create column names using our label-fn
      (let [rsmeta (.getMetaData rs)
            col-count (.getColumnCount rsmeta)
            cols (mapv #(label-fn rsmeta %) (range 1 (inc col-count)))]
        (jdbc.result-set/->MapResultSetBuilder rs rsmeta cols)))
    ;; Column reader function
    column-reader))

(def jdbc-options
  {:column-fn  ->snake-case
   :table-fn   csk/->snake_case
   :builder-fn (make-builder-fn)})

(defn load-schema!
  "Load the SQLite schema into the database.
   Uses db/schema.sql which is maintained by migrate.sh."
  [{:keys [database-path schema-dump-file]
    :or {schema-dump-file "db/schema.sql"}}]
  (shell/sh "sqlite3" "-init" schema-dump-file database-path ""))

(defn schema-initialized?
  "Check if the database schema has been initialized by looking for core tables."
  [db]
  (try
    (let [result (first (jdbc/execute! db
                                       ["SELECT COUNT(*) as cnt FROM sqlite_master WHERE type='table' AND name='user'"]))]
      (pos? (:cnt result)))
    (catch Exception _
      false)))
