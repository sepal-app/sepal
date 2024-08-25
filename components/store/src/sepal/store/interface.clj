(ns sepal.store.interface
  (:require [sepal.store.core :as core]))

(def transformer core/transformer)

(defn coerce [spec data]
  (core/coerce spec data))

(defn encode [spec data]
  (core/encode spec data))

(defn get-by-id
  ([db table id]
   (core/get-by-id db table id nil))
  ([db table id spec]
   (core/get-by-id db table id spec)))

(defn update!
  ([db table id data]
   (core/update! db table id data nil))
  ([db table id data spec]
   (core/update! db table id data spec)))

(defn create!
  ([db table data]
   (core/create! db table data nil))
  ([db table data spec]
   (core/create! db table data spec)))
