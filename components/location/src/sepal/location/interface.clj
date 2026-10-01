(ns sepal.location.interface
  (:require [integrant.core :as ig]
            [sepal.location.core :as core]
            [sepal.search.interface :as search.i]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn subtree
  "A HoneySQL subquery selecting the ids of every location matching `pred`,
  over `location l`, and of every location below one."
  [pred]
  (core/subtree pred))

(defn paths
  "{id [location ...]} for each of `ids`, root first and ending with the
  location itself, in one query."
  [db ids]
  (core/paths db ids))

(defn subtree-filter
  "A search field's :filter-clause: match `location-field` against location
  rows, as `l`, the way that field type always matches, then compare
  `id-column` against those locations and everything below them. Filtering by
  the Orchard means the Orchard's rows too. A value that matches nothing to
  match on, such as a blank full-text one, narrows nothing, as it would on the
  field itself."
  [id-column location-field]
  (fn [parsed]
    (when-let [clause (search.i/field-clause parsed location-field)]
      [:in id-column (core/subtree clause)])))

(defn count-children
  "Direct children of this location, or only those with `status`."
  ([db id]
   (core/count-children db id))
  ([db id status]
   (core/count-children db id status)))

(defn list-children
  "Direct children of this location, by code."
  [db id]
  (core/list-children db id))

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
