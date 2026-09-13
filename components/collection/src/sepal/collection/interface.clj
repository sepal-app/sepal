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

(defn delete!
  "Delete this collection's row. Deletes nothing else: what a collection owns
  and what blocks it are policy, and policy lives in the base -- see
  bases/app/src/sepal/app/delete.clj."
  [db id]
  (core/delete! db id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))

