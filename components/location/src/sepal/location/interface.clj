(ns sepal.location.interface
  (:require [integrant.core :as ig]
            [sepal.location.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn set-status!
  "Archive this location, or bring it back.

  Archiving is how a location that has a past leaves the garden: it can never
  be deleted, because material_change names it as the source or destination of
  moves that already happened. Whether the location is empty enough to archive
  is policy and lives in the base -- see bases/app/src/sepal/app/delete.clj."
  [db id status]
  (core/set-status! db id status))

(defn delete!
  "Delete this location's row. Deletes nothing else: what a location owns and
  what blocks it are policy, and policy lives in the base -- see
  bases/app/src/sepal/app/delete.clj."
  [db id]
  (core/delete! db id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
