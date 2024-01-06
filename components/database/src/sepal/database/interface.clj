(ns sepal.database.interface
  (:refer-clojure :exclude [count])
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.date-time]
            [sepal.database.core :as core]
            [sepal.database.postgresql :as pg]))

;; read all db date times as java.time.Instant
(next.jdbc.date-time/read-as-instant)

(def transformer core/transformer)

(defn coerce [spec data]
  (m/coerce spec data transformer))

(defn encode [spec data]
  (m/encode spec data transformer))

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

(defn count
  ([db stmt]
   (count db stmt nil))
  ([db stmt opts]
   (core/count db stmt opts)))

(defmacro with-transaction [[sym transactable opts] & body]
  `(jdbc/with-transaction+options [~sym ~transactable ~opts]
     ~@body))

(defn ->jsonb [value]
  (pg/->jsonb value))

(defmethod ig/init-key ::db [_ {:keys [connectable jdbc-options]}]
  (core/init-db :connectable connectable
                :jdbc-options jdbc-options))

(defmethod ig/init-key ::pool [_ {:keys [db-spec]}]
  (core/init-pool :db-spec db-spec))
