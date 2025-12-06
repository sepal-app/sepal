(ns sepal.collection.interface
  (:require [integrant.core :as ig]
            [sepal.collection.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn get-by-accession-id [db accession-id]
  (core/get-by-accession-id db accession-id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))

