(ns sepal.collection.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.collection.core :as core]
            [sepal.collection.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :collection id spec/Collection))

(defn create! [db data]
  (store.i/create! db :collection data spec/CreateCollection spec/Collection))

(defn update! [db id data]
  (store.i/update! db :collection id data spec/UpdateCollection spec/Collection))

(create-ns 'sepal.collection.interface)
(alias 'acc.i 'sepal.collection.interface)

(defn factory [{:keys [db taxon] :as args}]
  (let [data (mg/generate spec/CreateCollection)
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::acc.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :collection {:id (:collection/id data)}))))
