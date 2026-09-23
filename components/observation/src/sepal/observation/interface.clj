(ns sepal.observation.interface
  (:require [integrant.core :as ig]
            [sepal.observation.core :as core]))

(defn get-by-id
  "One observation, or nil when id doesn't match a row. Carries
  :observation/observer, :observation/author-email, :observation/type-label
  and :observation/value-label -- see `get-for-resource`."
  [db id]
  (core/get-by-id db id))

(defn get-for-resource
  "A resource's observations, newest observed first. Each carries
  :observation/observer -- :observation/observed-by when it's set, else the
  creating user's email, and nil when neither is present -- plus
  :observation/author-email, :observation/type-label and
  :observation/value-label; author-email is nil for a row with no author and
  value-label is nil for a general observation, which has no value."
  [db resource-type resource-id]
  (core/get-for-resource db resource-type resource-id))

(defn observer
  "The same fallback `get-for-resource` computes into
  `:observation/observer`, for a caller with its own
  `:observation/observed-by` and `:observation/author-email` columns rather
  than a full observation map -- the observation index's own query, which
  joins those in itself."
  [observation]
  (core/observer observation))

(defn count-for-resource
  "How many observations a resource has."
  [db resource-type resource-id]
  (core/count-for-resource db resource-type resource-id))

(defn due
  "Observations whose next_check_on is on or before `on-date`, oldest first,
  leaving out any followed up by a later observation of the same subject and
  type. `on-date` is an ISO-8601 string. Rows with no next_check_on never
  appear.
  Each carries :observation/observer -- see `get-for-resource`."
  [db on-date]
  (core/due db on-date))

(defn count-due
  "How many observations are due, the same set `due` returns, without
  materialising them."
  [db on-date]
  (core/count-due db on-date))

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
