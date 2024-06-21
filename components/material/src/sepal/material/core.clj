(ns sepal.material.core
  (:require [malli.core :as m]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.material.interface.spec :as spec]))

(defn get-by-id [db id]
  (let [result (jdbc.sql/get-by-id db :material id)]
    (m/coerce spec/Material result db.i/transformer)))

(defn create! [db data]
  ;; TODO: Create auditing event
  (try
    (let [data (m/coerce spec/CreateMaterial data db.i/transformer)
          result  (jdbc.sql/insert! db
                                    :material
                                    data
                                    {:return-keys true})]
      (tap> (str "data: " data))
      (m/coerce spec/Material result db.i/transformer))
    (catch Exception ex
      (tap> (str "ex: " ex))
      (error.i/ex->error ex))))

(defn update! [db id data]
  (try
    (let [data (m/coerce spec/UpdateMaterial data db.i/transformer)
          result (jdbc.sql/update! db
                                   :material
                                   data
                                   {:id id}
                                   {:return-keys 1})]
      (m/coerce spec/Material result db.i/transformer))
    (catch Exception ex
      (error.i/ex->error ex))))
