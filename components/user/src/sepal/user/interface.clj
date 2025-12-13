(ns sepal.user.interface
  (:require [integrant.core :as ig]
            [sepal.user.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn get-by-email [db email]
  (core/get-by-email db email))

(defn exists? [db email]
  (core/exists? db email))

(defn create! [db data]
  (core/create! db data))

(defn set-password! [db id password]
  (core/set-password! db id password))

(defn verify-password [db email password]
  (core/verify-password db email password))

(defn update! [db id data]
  (core/update! db id data))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
