(ns sepal.location.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.location.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :location id spec/Location))

(defn create! [db data]
  (store.i/create! db :location data spec/CreateLocation spec/Location))

(defn update! [db id data]
  (store.i/update! db :location id data spec/UpdateLocation spec/Location))

(create-ns 'sepal.location.interface)
(alias 'loc.i 'sepal.location.interface)

(defn factory [{:keys [db] :as args}]
  (let [data (mg/generate spec/CreateLocation)
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::loc.i/factory [_ {:location/keys [id] :as data}]
  (when id
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :location {:id id}))))
