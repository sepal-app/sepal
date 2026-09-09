(ns sepal.tag.interface
  (:require [integrant.core :as ig]
            [sepal.tag.core :as core]))

(defn get-by-id [db id] (core/get-by-id db id))
(defn get-by-name [db name] (core/get-by-name db name))
(defn list-all [db] (core/list-all db))
(defn create! [db data] (core/create! db data))
(defn update! [db id data] (core/update! db id data))
(defn delete! [db id] (core/delete! db id))
(defn tag! [db tag-id resource-id resource-type] (core/tag! db tag-id resource-id resource-type))
(defn untag! [db tag-id resource-id resource-type] (core/untag! db tag-id resource-id resource-type))
(defn get-for-resource [db resource-type resource-id] (core/get-for-resource db resource-type resource-id))
(defn get-tagged [db tag-id] (core/get-tagged db tag-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
