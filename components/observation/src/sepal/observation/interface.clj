(ns sepal.observation.interface
  (:require [integrant.core :as ig]
            [sepal.observation.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn get-for-resource
  "A resource's observations, newest observed first. Each carries
  :observation/author-email, :observation/type-label and
  :observation/value-label; the first is nil for a row with no author and the
  last is nil for a general observation, which has no value."
  [db resource-type resource-id]
  (core/get-for-resource db resource-type resource-id))

(defn count-for-resource
  "How many observations a resource has."
  [db resource-type resource-id]
  (core/count-for-resource db resource-type resource-id))

(defn due
  "Observations whose next_check_on is on or before `on-date`, oldest first.
  `on-date` is an ISO-8601 string. Rows with no next_check_on never appear."
  [db on-date]
  (core/due db on-date))

(defn list-types
  "Every observation_type, ordered by code. Drives the Type field's options."
  [db]
  (core/list-types db))

(defn list-values
  "Every observation_value, ordered by type then code. Drives the Value
  field's options, filtered to the selected type in the browser."
  [db]
  (core/list-values db))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn delete! [db id]
  (core/delete! db id))

(defn delete-for-resource!
  "Every observation on one resource. The polymorphic resource_id carries no
  foreign key, so this is the cascade."
  [db resource-type resource-id]
  (core/delete-for-resource! db resource-type resource-id))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
