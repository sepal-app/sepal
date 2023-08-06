(ns sepal.database.core
  (:require [camel-snake-kebab.core :as csk]
            [honey.sql]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as jdbc.connection]
            [next.jdbc.result-set :as jdbc.result-set]
            [sepal.database.honeysql :as honeysql]
            [sepal.database.postgresql :as postgresql])
  (:import [com.zaxxer.hikari HikariDataSource]))

(def default-jdbc-options
  (-> jdbc/snake-kebab-opts
      ;; override the column-fn b/c the default behavior of ->snake_case is to
      ;; turn :s3-bucket into s_3_bucket
      (assoc :column-fn (fn [v]
                          (csk/->snake_case v :separator \-)))
      ;; override the builder-fn b/c the default behavior of ->kebab-case is to
      ;; turn :s3_bucket into s-3-bucket
      (assoc :builder-fn (fn [rs opts]
                           (jdbc.result-set/as-modified-maps
                            rs
                            (assoc opts
                                   :label-fn
                                   (fn [v]
                                     (csk/->kebab-case v :separator \_))))))))

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
                    (= (:db-type db-spec) "postgresql")
                    (assoc :connectionInitSql "COMMIT;"))]
      (jdbc.connection/->pool HikariDataSource db-spec))))

(create-ns 'sepal.database.interface)
(alias 'db.i 'sepal.database.interface)

(defmethod ig/halt-key! ::db.i/db [_ db]
  (-> db jdbc/get-datasource .close))

(defn execute! [db stmt]
  (let [stmt (if (map? stmt)
               (honey.sql/format stmt)
               stmt)]
    (jdbc/execute! db stmt)))

(defn execute-one! [db stmt]
  (let [stmt (if (map? stmt)
               (honey.sql/format stmt)
               stmt)]
    (jdbc/execute-one! db stmt)))
