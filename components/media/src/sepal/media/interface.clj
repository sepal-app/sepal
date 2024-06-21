(ns sepal.media.interface
  (:require [integrant.core :as ig]
            [sepal.media.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn get-link [db media-id]
  (core/get-link db media-id))

(defn create! [db data]
  (core/create! db data))

(defn link! [db id resource-id resource-type]
  (core/link! db id resource-id resource-type))

(defn delete!
  "Delete a media object from the database.

  This does not delete the object from the remote storage, e.g. s3.
  "
  [db id]
  (core/delete! db id))

(defn unlink! [db id]
  (core/unlink! db id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
