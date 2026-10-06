(ns sepal.tag.interface
  (:require [integrant.core :as ig]
            [sepal.tag.core :as core]))

(defn get-by-id [db id] (core/get-by-id db id))
(defn get-by-name [db name] (core/get-by-name db name))
(defn list-all
  "Every tag with its link count, including a tag with none. Takes an
  `:order-by` option of HoneySQL order-by terms; the default is by name."
  [db & {:as opts}]
  (core/list-all db opts))
(defn create! [db data] (core/create! db data))
(defn update! [db id data] (core/update! db id data))
(defn delete! [db id] (core/delete! db id))
(defn tag! [db tag-id resource-id resource-type] (core/tag! db tag-id resource-id resource-type))
(defn untag! [db tag-id resource-id resource-type] (core/untag! db tag-id resource-id resource-type))

(defn delete-for-resource!
  "Every tag link on one resource. The tags themselves are not touched -- a tag
  is a garden-wide label, and unlinking is the whole of what a deleted record
  owes it."
  [db resource-type resource-id]
  (core/delete-for-resource! db resource-type resource-id))

(defn get-for-resource [db resource-type resource-id] (core/get-for-resource db resource-type resource-id))
(defn get-for-resources [db resource-type ids] (core/get-for-resources db resource-type ids))
(defn get-tagged [db tag-id] (core/get-tagged db tag-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
