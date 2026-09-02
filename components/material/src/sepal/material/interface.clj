(ns sepal.material.interface
  (:require [integrant.core :as ig]
            [sepal.material.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn create-change!
  "Record a history row directly. The import path uses this; interactive
  writes go through `update!`."
  [db data]
  (core/create-change! db data))

(defn list-reasons
  "Every material_change_reason, ordered by code."
  [db]
  (core/list-reasons db))

(defn list-by-material-id
  "A material's change history, newest first, each row carrying the reason
  label."
  [db material-id]
  (core/list-by-material-id db material-id))

(defn moved-out-by-location-id
  "Change rows whose material left this location, most recent first, with the
  material code and destination name."
  [db location-id]
  (core/moved-out-by-location-id db location-id))

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

(defn count-all
  "Count every material in the garden."
  [db]
  (core/count-all db))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
