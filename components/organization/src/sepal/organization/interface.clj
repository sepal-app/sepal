(ns sepal.organization.interface
  (:require [honey.sql :as sql]
            [integrant.core :as ig]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.organization.core :as core]))

(defn parse-int [v]
  (if-not (int? v)
    (Integer/parseInt v)
    v))

(defn get-by-id [db id]
  (jdbc.sql/get-by-id db :organization (parse-int id)))

(defn create! [db data]
  (core/create! db data))

(defn assign-role [db data]
  (let [stmt (-> {:insert-into :organization-user
                  :values [data]
                  :returning :*}
                 (sql/format))]
    (try
      (jdbc/execute-one! db stmt)
      (catch Exception e
        {:error {:message (ex-message e)}}))))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
