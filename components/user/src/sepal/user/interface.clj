(ns sepal.user.interface
  (:require [integrant.core :as ig]
            [sepal.user.core :as core]))

(defn get-by-id [db id]
  (core/get-by-id db id))

(defn get-by-email [db email]
  (core/get-by-email db email))

(defn get-all
  "Returns all users, optionally filtered.
   Options:
   - :q      - Search by name or email
   - :status - Filter by status (:active, :archived, :invited)"
  [db & {:keys [q status]}]
  (core/get-all db :q q :status status))

(defn count-by-role [db role]
  (core/count-by-role db role))

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

(defn archive! [db id]
  (core/update! db id {:status :archived}))

(defn activate! [db id]
  (core/update! db id {:status :active}))

(defmethod ig/init-key ::factory [_ args]
  (core/factory args))
