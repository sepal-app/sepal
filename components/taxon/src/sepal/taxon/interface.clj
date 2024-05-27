(ns sepal.taxon.interface
  (:require [integrant.core :as ig]
            [sepal.taxon.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defn wfo-id? [id]
  (core/wfo-id? id))
(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
