(ns sepal.contact.interface
  (:require [integrant.core :as ig]
            [sepal.contact.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn delete!
  "Delete this contact's row. Deletes nothing else: what a contact owns and
  what blocks it are policy, and policy lives in the base -- see
  bases/app/src/sepal/app/delete.clj."
  [db id]
  (core/delete! db id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
