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
    (jdbc.types/as-other rank )))

(defn create! [db data]
  ;; TODO: Create auditing event
  (try
    (let [data (-> (m/coerce spec/CreateTaxon data db.i/transformer)
                   (update :rank rank->pg-enum))
          result  (jdbc.sql/insert! db
                                    :taxon
                                    data
                                    {:return-keys true})]
      (m/coerce spec/Taxon result db.i/transformer))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn update! [db id data]
  (try
    (let [data (m/coerce spec/UpdateTaxon data db.i/transformer)
          result (jdbc.sql/update! db
                                :taxon
                                data
                                {:id id}
                                {:return-keys 1})]
      (m/coerce spec/Taxon result db.i/transformer))
    (catch Exception e
      (ex-data e))))
