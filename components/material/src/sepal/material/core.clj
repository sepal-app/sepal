(ns sepal.material.core
  (:require [sepal.material.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :material id spec/Material))

(defn update! [db id data]
  (store.i/update! db :material id data spec/UpdateMaterial))

(defn create! [db data]
  (store.i/create! db :material data spec/CreateMaterial))
