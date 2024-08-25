(ns sepal.location.core
  (:require [sepal.location.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :location id spec/Location))

(defn create! [db data]
  (store.i/create! db :location data spec/CreateLocation))

(defn update! [db id data]
  (store.i/update! db :location id data spec/UpdateLocation))
