(ns sepal.taxon.core
  (:require [malli.core :as m]
            [next.jdbc.sql :as jdbc.sql]
            [next.jdbc.types :as jdbc.types]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.core :as core]
            [sepal.taxon.interface.spec :as spec]))

(defn rank->pg-enum [rank]
  (when (some? rank)
    (jdbc.types/as-other rank)))

(defn create! [db data]
  ;; TODO: Create auditing event
  (try
    (let [data (-> (db.i/coerce spec/CreateTaxon data)
                   (update :rank rank->pg-enum))
          result  (jdbc.sql/insert! db
                                    :taxon
                                    (db.i/encode spec/CreateTaxon data)
                                    {:return-keys true})]
      (db.i/coerce spec/Taxon result))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn update! [db id data]
  (try
    (let [data (db.i/coerce spec/UpdateTaxon data)
          result (jdbc.sql/update! db
                                   :taxon
                                   (db.i/encode spec/UpdateTaxon data)
                                   {:id id}
                                   {:return-keys true})]
      (db.i/coerce spec/Taxon result))
    (catch Exception ex
      (error.i/ex->error ex))))
