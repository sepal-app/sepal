(ns sepal.accession.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.core :as core]
            [sepal.accession.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :accession id spec/Accession))

(defn create! [db data]
  (store.i/create! db :accession data spec/CreateAccession spec/Accession))

(defn update! [db id data]
  (store.i/update! db :accession id data spec/UpdateAccession spec/Accession))

(create-ns 'sepal.accession.interface)
(alias 'acc.i 'sepal.accession.interface)

(defn factory [{:keys [db taxon] :as args}]
  (let [data (-> (mg/generate spec/CreateAccession)
                 (assoc :taxon-id (:taxon/id taxon)))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::acc.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :accession {:id (:accession/id data)}))))
