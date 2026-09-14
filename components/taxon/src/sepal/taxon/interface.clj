(ns sepal.taxon.interface
  (:require [integrant.core :as ig]
            [sepal.taxon.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn list-by-wfo-taxon-id [db wfo-taxon-id]
  (core/list-by-wfo-taxon-id db wfo-taxon-id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn delete!
  "Delete this taxon's row. Deletes nothing else: what a taxon owns and what
  blocks it are policy, and policy lives in the base -- see
  bases/app/src/sepal/app/delete.clj."
  [db id]
  (core/delete! db id))

(defn count-children
  "How many taxa name this one as their parent."
  [db taxon-id]
  (core/count-children db taxon-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
