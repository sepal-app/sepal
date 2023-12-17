(ns sepal.material.interface
  (:require [sepal.material.core :as core])
  )

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))
