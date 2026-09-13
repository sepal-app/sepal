(ns sepal.taxon.interface
  (:require [integrant.core :as ig]
            [sepal.taxon.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn list-by-wfo-taxon-id [db wfo-taxon-id]
  (core/list-by-wfo-taxon-id db wfo-taxon-id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
