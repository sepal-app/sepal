(ns sepal.material.interface
  (:require [integrant.core :as ig]
            [sepal.material.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn delete!
  "Delete this material's row. Deletes nothing else: what material owns and
  what blocks it are policy, and policy lives in the base -- see
  bases/app/src/sepal/app/delete.clj."
  [db id]
  (core/delete! db id))

(defn count-changes-by-location-id
  "How many material_change rows name this location as a source or a
  destination."
  [db location-id]
  (core/count-changes-by-location-id db location-id))

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

(defn moved-out-of-locations
  "Change rows whose material left `location-ids`, a subquery, for somewhere
  outside them, or was removed, most recent first, with the material code."
  [db location-ids]
  (core/moved-out-of-locations db location-ids))

(defn list-by-propagation-id
  "Material this propagation produced, by code."
  [db propagation-id]
  (core/list-by-propagation-id db propagation-id))

(defn list-by-accession-id
  "Every material of one accession, by code."
  [db accession-id]
  (core/list-by-accession-id db accession-id))

(defn next-code
  "The next material code within this accession, or nil when the template is
  unusable."
  ([db template accession-id]
   (core/next-code db template accession-id))
  ([db template accession-id date]
   (core/next-code db template accession-id date)))

(defn count-by-accession-id
  "Count materials for a given accession."
  [db accession-id]
  (core/count-by-accession-id db accession-id))

(defn count-by-location-id
  "Count materials standing directly in this location, not in its
  sub-locations. The delete and archive blockers rely on that: an orchard is
  emptied of what stands in it, not of what is in its rows. The location
  panel's count covers sub-locations, through count-in-locations."
  [db location-id]
  (core/count-by-location-id db location-id))

(defn count-in-locations
  "Count materials in any of `location-ids`, a subquery."
  [db location-ids]
  (core/count-in-locations db location-ids))

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
