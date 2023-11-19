(ns sepal.accession.core
  (:require [malli.core :as m]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.accession.core :as core]
            [sepal.accession.interface.spec :as spec]))

(defn get-by-id [db id]
  (let [result (jdbc.sql/get-by-id db :accession id)]
    (m/coerce spec/Accession result db.i/transformer)))

(defn create! [db data]
  ;; TODO: Create auditing event
  (try
    (let [data (m/coerce spec/CreateAccession data db.i/transformer)
          result  (jdbc.sql/insert! db
                                    :accession
                                    data
                                    {:return-keys true})]
      (m/coerce spec/Accession result db.i/transformer))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn update! [db id data]
  (try
    (let [data (m/coerce spec/UpdateAccession data db.i/transformer)
          result (jdbc.sql/update! db
                                :accession
                                data
                                {:id id}
                                {:return-keys 1})]
      (m/coerce spec/Accession result db.i/transformer))
    (catch Exception ex
      (error.i/ex->error ex))))
