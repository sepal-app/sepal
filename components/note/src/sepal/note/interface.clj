(ns sepal.note.interface
  (:require [integrant.core :as ig]
            [sepal.note.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn get-for-resource
  "A resource's notes, newest first. Each carries :note/author-email, which is
  nil for an imported note that has no author."
  [db resource-type resource-id]
  (core/get-for-resource db resource-type resource-id))

(defn count-for-resource
  "How many notes a resource has."
  [db resource-type resource-id]
  (core/count-for-resource db resource-type resource-id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn delete! [db id]
  (core/delete! db id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
