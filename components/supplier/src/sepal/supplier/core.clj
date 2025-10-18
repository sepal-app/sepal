(ns sepal.supplier.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.store.interface :as store.i]
            [sepal.supplier.core :as core]
            [sepal.supplier.interface.spec :as spec]))

(defn get-by-id [db id]
  (store.i/get-by-id db :supplier id spec/Supplier))

(defn create! [db data]
  (store.i/create! db :supplier data spec/CreateSupplier spec/Supplier))

(defn update! [db id data]
  (store.i/update! db :supplier id data spec/UpdateSupplier spec/Supplier))

(create-ns 'sepal.supplier.interface)
(alias 'acc.i 'sepal.supplier.interface)

(defn factory [{:keys [db taxon] :as args}]
  (let [data (mg/generate spec/CreateSupplier)
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::acc.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :supplier {:id (:supplier/id data)}))))
