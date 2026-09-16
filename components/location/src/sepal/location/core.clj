(ns sepal.location.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.location.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :location id spec/Location))

(defn create! [db data]
  (store.i/create! db :location data spec/CreateLocation spec/Location))

(defn update! [db id data]
  (store.i/update! db :location id data spec/UpdateLocation spec/Location))

(defn set-status! [db id status]
  (update! db id {:status status}))

(defn delete! [db id]
  (jdbc.sql/delete! db :location {:id id})
  nil)

(create-ns 'sepal.location.interface)
(alias 'loc.i 'sepal.location.interface)

(defonce ^:private factory-code-seq (atom 0))

(defn factory [{:keys [db] :as args}]
  (let [data (-> (mg/generate spec/CreateLocation)
                 ;; A location code is unique in the garden, and the spec asks
                 ;; only for two characters -- generated ones collide often
                 ;; enough to fail a suite at random, on whichever test drew
                 ;; second.
                 (assoc :code (format "L%05d" (swap! factory-code-seq inc))))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::loc.i/factory [_ {:location/keys [id] :as data}]
  (when id
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :location {:id id}))))
