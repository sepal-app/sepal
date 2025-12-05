(ns sepal.contact.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.contact.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :contact id spec/Contact))

(defn create! [db data]
  (store.i/create! db :contact data spec/CreateContact spec/Contact))

(defn update! [db id data]
  (store.i/update! db :contact id data spec/UpdateContact spec/Contact))

(create-ns 'sepal.contact.interface)
(alias 'contact.i 'sepal.contact.interface)

(defn factory [{:keys [db] :as args}]
  (let [data (mg/generate spec/CreateContact)
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::contact.i/factory [_ {:contact/keys [id] :as data}]
  (when id
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :contact {:id id}))))
