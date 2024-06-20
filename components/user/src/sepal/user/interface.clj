(ns sepal.user.interface
  (:require [integrant.core :as ig]
            [sepal.user.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn exists? [db id-or-email]
  (core/exists? db id-or-email))

(defn create! [db data]
  (core/create! db data))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
