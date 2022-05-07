(ns sepal.taxon.interface
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [next.jdbc.sql :as jdbc.sql]
            [next.jdbc.types :as jdbc.types]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface.spec :as spec]))

(defn- db-> [data]
  data)

(defn- ->db [data]
  (->>
   (cond-> data
     (:rank data)
     (update :rank #(when (seq %) (jdbc.types/as-other %))))
   (cske/transform-keys csk/->snake_case)))

(defn get-by-id [db id]
  (-> (jdbc.sql/get-by-id db :taxon id)
      (db->)))

(defn query
  [db & params]
  (->> (apply db.i/query db :taxon params)
       (map db->)))

(defn create!
  [db data]
  (try
    (jdbc.sql/insert! db :taxon (->db data))
    (catch Exception e
      {:error {:message (ex-message e)}})))

(defn update! [db id data]
  (try
    (jdbc.sql/update! db :taxon (->db data) [:= :id id])
    (catch Exception e
      {:error {:message (ex-message e)}})))
