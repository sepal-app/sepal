(ns sepal.database.core
  (:refer-clojure :exclude [count])
  (:require [camel-snake-kebab.core :as csk]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [honey.sql]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc.result-set])
  (:import [java.sql ResultSet ResultSetMetaData]))

(def builder-fn (jdbc.result-set/builder-adapter
                  ;; (next.jdbc.result-set/->MapResultSetBuilder rs rsmeta cols)
                  jdbc.result-set/as-kebab-maps
                  (fn [builder ^ResultSet rs ^Integer i]
                    (let [rsm ^ResultSetMetaData (:rsmeta builder)]
                      (jdbc.result-set/read-column-by-index
                        (if (#{"BIT" "BOOL" "BOOLEAN"} (.getColumnTypeName rsm i))
                          (.getBoolean rs i)
                          (.getObject rs i))
                        rsm
                        i)))))

(defn ->kebab-case
  "Same as csk/->kebab-case but only use '_' as the separator.

  This is needed so a column like :s3_bucket doesn't get turned into :s-3-bucket
  "
  [v]
  (when (seq v)
    (csk/->kebab-case v :separator \_)))

(defn ->snake-case
  "Same as csk/->skae-case but only use '-' as the separator.

  This is needed so a column like :s3-bucket doesn't get turned into :s_3_bucket
  "
  [v]
  (when (seq v)
    (csk/->snake_case v :separator \-)))

(defn get-table-name
  "Copied from the private function next.jdbc.result-set/get-table-name."
  [^java.sql.ResultSetMetaData rsmeta ^Integer i]
  (try
    (.getTableName rsmeta i)
    (catch java.sql.SQLFeatureNotSupportedException _
      nil)))

(def jdbc-options
  (merge jdbc/snake-kebab-opts
         {:column-fn ->snake-case
          :label-fn  (fn [rsmeta i]
                       (let [[ns label] (str/split (.getColumnLabel rsmeta i) #"__" 2)]
                         (if (seq label)
                           (keyword (->kebab-case ns)
                                    (->kebab-case label))
                           (keyword (->kebab-case (get-table-name rsmeta i))
                                    (->kebab-case (.getColumnLabel rsmeta i))))))

           ;; override the builder-fn b/c the default behavior of ->kebab-case is to
           ;; turn :s3_bucket into s-3-bucket
          :builder-fn builder-fn}))

(defn load-schema!
  "Load the SQLite schema into the in-memory test database.
   Uses db/schema.sql which is maintained by dbmate dump."
  [datasource]
  (let [schema-file (io/file "db/schema.sql")]
    (if (.exists schema-file)
      (let [sql (slurp schema-file)
            ;; Split SQL into individual statements
            statements (->> (clojure.string/split sql #";")
                            (map clojure.string/trim)
                            (remove clojure.string/blank?))]
        (doseq [statement statements]
          (try
            (jdbc/execute! datasource [statement])
            (catch Exception e
              ;; Ignore errors for duplicate objects and constraint violations
              ;; These can happen when the schema is loaded multiple times
              (when-not (or (re-find #"already exists" (.getMessage e))
                            (re-find #"duplicate column name" (.getMessage e))
                            (re-find #"UNIQUE constraint failed" (.getMessage e))
                            (re-find #"PRIMARY KEY constraint failed" (.getMessage e)))
                (throw e))))))
      (throw (ex-info "Schema file not found. Run 'dbmate dump' to generate it."
                      {:file "db/schema.sql"})))))
