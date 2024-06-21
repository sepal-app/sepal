(ns sepal.location.core
  (:require [malli.core :as m]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface.spec :as spec]))

(defn get-by-id [db id]
  (let [result (jdbc.sql/get-by-id db :location id)]
    (m/coerce spec/Location result db.i/transformer)))

(defn create! [db data]
  ;; TODO: Create auditing event
  (try
    (let [data (m/coerce spec/CreateLocation data db.i/transformer)
          result  (jdbc.sql/insert! db
                                    :location
                                    data
                                    {:return-keys true})]
      (m/coerce spec/Location result db.i/transformer))
    (catch Exception ex
      (tap> (str "ex: " ex))
      (error.i/ex->error ex))))

(defn update! [db id data]
  (try
    (let [data (m/coerce spec/UpdateLocation data db.i/transformer)
          result (jdbc.sql/update! db
                                   :location
                                   data
                                   {:id id}
                                   {:return-keys 1})]
      (m/coerce spec/Location result db.i/transformer))
    (catch Exception ex
      (error.i/ex->error ex))))
