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
   (update! db table id data nil nil))
  ([db table id data input-spec]
   (update! db table id data input-spec nil))
  ([db table id data input-spec result-spec]
   (core/update! db table id data input-spec result-spec)))

(defn create!
  ([db table data]
   (create! db table data nil nil))
  ([db table data input-spec]
   (create! db table data input-spec nil))
  ([db table data input-spec result-spec]
   (core/create! db table data input-spec result-spec)))
