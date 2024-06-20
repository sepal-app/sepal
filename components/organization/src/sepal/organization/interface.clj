(ns sepal.organization.interface
  (:require [integrant.core :as ig]
            [sepal.organization.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn get-user-org [db user-id]
  (core/get-user-org db user-id))

(defn create! [db data]
  (core/create! db data))

(defn assign-role! [db data]
  (core/assign-role! db data))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
