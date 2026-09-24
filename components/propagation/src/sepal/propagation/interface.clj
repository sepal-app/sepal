(ns sepal.propagation.interface
  (:require [integrant.core :as ig]
            [sepal.propagation.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn delete!
  "Delete this propagation's row. Deletes nothing else: a product that came
  out of it is a plant in the garden and outlives the record of how it got
  there. What blocks a delete is policy, and policy lives in the base -- see
  bases/app/src/sepal/app/delete.clj."
  [db id]
  (core/delete! db id))

(defn list-types [db]
  (core/list-types db))

(defn list-statuses [db]
  (core/list-statuses db))

(defn list-by-parent-accession-id [db accession-id]
  (core/list-by-parent-accession-id db accession-id))

(defn list-by-parent-material-id [db material-id]
  (core/list-by-parent-material-id db material-id))

(defn list-by-location-id [db location-id]
  (core/list-by-location-id db location-id))

(defn count-by-parent-accession-id [db accession-id]
  (core/count-by-parent-accession-id db accession-id))

(defn count-by-parent-material-id [db material-id]
  (core/count-by-parent-material-id db material-id))

(defn count-by-rootstock-taxon-id [db taxon-id]
  (core/count-by-rootstock-taxon-id db taxon-id))

(defn count-by-location-id [db location-id]
  (core/count-by-location-id db location-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
