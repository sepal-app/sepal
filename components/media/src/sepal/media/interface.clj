(ns sepal.media.interface
  (:require [sepal.media.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))
