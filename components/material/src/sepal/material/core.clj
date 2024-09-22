(ns sepal.material.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.material.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :material id spec/Material))

(defn update! [db id data]
  (store.i/update! db :material id data spec/UpdateMaterial spec/Material))

(defn create! [db data]
  (store.i/create! db :material data spec/CreateMaterial spec/Material))

(create-ns 'sepal.material.interface)
(alias 'mat.i 'sepal.material.interface)

(defn factory [{:keys [db organization accession location] :as args}]
  (let [data (-> (mg/generate spec/CreateMaterial)
                 (assoc :organization-id (:organization/id organization))
                 (assoc :accession-id (:accession/id accession))
                 (assoc :location-id (:location/id location)))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::mat.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :material {:id (:material/id data)}))))
