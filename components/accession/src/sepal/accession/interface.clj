(ns sepal.accession.interface
  (:require [sepal.accession.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

#_(defn find [db data]
  (core/find db data))

(defn create! [db data]
  (core/create! db data))

(defn update! [db id data]
  (core/update! db id data))
