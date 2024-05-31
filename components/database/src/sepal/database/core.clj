(ns sepal.database.core
  (:refer-clojure :exclude [count])
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [honey.sql]
            [integrant.core :as ig]
            [malli.transform :as mt]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as jdbc.connection]
            [next.jdbc.result-set :as jdbc.result-set]
            [sepal.database.honeysql :as honeysql]
            [sepal.database.postgresql :as postgresql])
  (:import [com.zaxxer.hikari HikariDataSource]))

(def transformer
  (mt/transformer
   mt/strip-extra-keys-transformer
   {:name :db}))

(defn get-modified-column-names [^java.sql.ResultSetMetaData rsmeta opts]
  (let [lf (:label-fn opts)]
    (assert lf ":label-fn is required")
    (mapv (fn [^Integer i]
            (lf rsmeta i))
          (range 1 (inc (if rsmeta (.getColumnCount rsmeta) 0))))))

(defn builder-fn [^java.sql.ResultSet rs opts]
  (let [rsmeta (.getMetaData rs)
        cols   (get-modified-column-names rsmeta opts)]
    ;; TODO: What about supporting something like parent__taxon__name to get an map back like
    ;; {:parent {:taxon/name "xxx"}}
    (next.jdbc.result-set/->MapResultSetBuilder rs rsmeta cols)))

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

(def default-jdbc-options
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

(defn init-db  [& {:keys [connectable jdbc-options]}]
  (postgresql/init)
  (honeysql/init)
  (jdbc/with-options connectable (or jdbc-options default-jdbc-options)))

(defn init-pool [& {:keys [db-spec]}]
  (when db-spec
    (let [db-spec (cond-> db-spec
                    ;; HikariCP expects :username
                    (not (:username db-spec))
                    (assoc :username (:user db-spec))

                    ;; When using postgresql use :connectionInitSql "COMMIT;" setting is required in case
                    ;; a default :schema is provided, see https://github.com/brettwooldridge/HikariCP/issues/1369
                    (= (:dbtype db-spec) "postgresql")
                    (assoc :connectionInitSql "COMMIT;"))]
      (jdbc.connection/->pool HikariDataSource db-spec))))

(create-ns 'sepal.database.interface)
(alias 'db.i 'sepal.database.interface)

(defmethod ig/halt-key! ::db.i/db [_ db]
  (-> db jdbc/get-datasource .close))

(defn execute! [db stmt opts]
  (let [stmt (if (map? stmt)
               (honey.sql/format stmt opts)
               stmt)]
    (jdbc/execute! db stmt opts)))

(defn execute-one! [db stmt opts]
  (let [stmt (if (map? stmt)
               (honey.sql/format stmt opts)
               stmt)]
    (jdbc/execute-one! db stmt opts)))

(defn exists?
  [db stmt]
  (-> (execute-one! db {:select [[[:exists stmt]]]} {})
      :exists))

(defn count
  [db stmt opts]
  (-> (execute-one! db
                    {:select [[[:count :*]]]
                     :from [[stmt :c]]}
                    opts)
      :count))
