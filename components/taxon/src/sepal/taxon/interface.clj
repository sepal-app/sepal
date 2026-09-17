(ns sepal.taxon.interface
  (:require [integrant.core :as ig]
            [sepal.taxon.core :as core]
            [sepal.taxon.parentage :as parentage]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn list-by-wfo-taxon-id [db wfo-taxon-id]
  (core/list-by-wfo-taxon-id db wfo-taxon-id))

(defn list-by-name
  "Every taxon with exactly this name. A vector: `taxon.name` is not unique."
  [db taxon-name]
  (core/list-by-name db taxon-name))

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

(def list-parentage
  "This taxon's parents, ordered as the formula is written."
  #'parentage/list-for-taxon)

(def list-parentage-children
  "The crosses naming this taxon as a parent."
  #'parentage/list-children)

(def count-parentage-children
  "How many crosses name this taxon as a parent."
  #'parentage/count-children)

(def delete-parentage!
  "Remove this taxon's own parentage rows."
  #'parentage/delete-for-taxon!)

(def set-parentage!
  "Replace this taxon's parentage with the given parents, in order."
  #'parentage/set-for-taxon!)

(defn count-children
  "How many taxa name this one as their parent."
  [db taxon-id]
  (core/count-children db taxon-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
