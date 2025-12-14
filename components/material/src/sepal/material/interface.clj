(ns sepal.material.interface
  (:require [integrant.core :as ig]
            [sepal.material.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn count-by-accession-id
  "Count materials for a given accession."
  [db accession-id]
  (core/count-by-accession-id db accession-id))

(defn count-by-location-id
  "Count materials at a given location."
  [db location-id]
  (core/count-by-location-id db location-id))

(defn count-by-taxon-id
  "Count materials for a given taxon (via accession)."
  [db taxon-id]
  (core/count-by-taxon-id db taxon-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
