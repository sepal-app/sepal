(ns sepal.taxon.interface
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [malli.core :as m]
            [malli.transform :as mt]
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
  (->> (apply db.i/execute! db :taxon params)
       (map db->)))

(defn create!
  [db data]
  ;; TODO: Create auditing event
  ;; TODO: Validate data against spec/CreateTaxon
  (try
    (let [data (m/coerce spec/CreateTaxon data)]
      (jdbc.sql/insert! db
                        :taxon
                        (->db data)
                        {:return-keys true}))
    (catch Exception e
      {:error {:message (ex-message e)}})))

(defn update! [db id data]
  (try
    (let [data (m/coerce spec/UpdateTaxon data db.i/transformer)
          row (jdbc.sql/update! db
                                :taxon
                                data
                                {:id id}
                                {:return-keys 1})]
      (m/coerce spec/Taxon row db.i/transformer))
    (catch Exception e
      {:error {:message (ex-message e)}})))
