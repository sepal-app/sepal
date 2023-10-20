(ns sepal.database.interface
  (:require [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [sepal.database.core :as core]))

;; read all db date times as java.time.Instant
(next.jdbc.date-time/read-as-instant)

(defn execute!
  ([db stmt]
   (core/execute! db stmt {}))
  ([db stmt opts]
   (core/execute! db stmt opts)))

(defn execute-one!
  ([db stmt]
   (core/execute-one! db stmt {}))
  ([db stmt opts]
   (core/execute-one! db stmt opts)))

(defn exists? [db stmt]
  (core/exists? db stmt))

(defmacro with-transaction [[sym transactable opts] & body]
  `(jdbc/with-transaction [~sym (core/->next-jdbc ~transactable) ~opts]
     (let [~sym (jdbc/with-options connectable ~sym)]
       ~@body)))

(defmethod ig/init-key ::db [_ {:keys [connectable jdbc-options]}]
  (core/init-db :connectable connectable
                :jdbc-options jdbc-options))

(defmethod ig/init-key ::pool [_ {:keys [db-spec]}]
  (core/init-pool :db-spec db-spec))
