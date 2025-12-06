(ns sepal.collection.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.collection.interface.spec :as spec]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]))

(def ^:private select-columns
  [:id
   :collected_date
   :collector
   :habitat
   :taxa
   :remarks
   :country
   :province
   :locality
   [[:json_object
     [:inline "lat"] [:ST_Y :geo_coordinates]
     [:inline "lng"] [:ST_X :geo_coordinates]
     [:inline "srid"] [:ST_SRID :geo_coordinates]]
    :collection__geo_coordinates]
   :geo_uncertainty
   :elevation
   :accession_id])

(defn- build-geo-coordinates
  "Build MakePoint SQL expression from geo-coordinates map."
  [{:keys [lat lng srid] :or {srid spec/default-srid}}]
  [:MakePoint lng lat srid])

(defn- prepare-insert-data
  "Prepare data for insert, handling geo-coordinates specially."
  [data]
  (if-let [geo (:geo-coordinates data)]
    (-> (dissoc data :geo-coordinates)
        (assoc :geo_coordinates (build-geo-coordinates geo)))
    (dissoc data :geo-coordinates)))

(defn- prepare-update-data
  "Prepare data for update, handling geo-coordinates specially."
  [data]
  (if (contains? data :geo-coordinates)
    (if-let [geo (:geo-coordinates data)]
      (-> (dissoc data :geo-coordinates)
          (assoc :geo_coordinates (build-geo-coordinates geo)))
      (-> (dissoc data :geo-coordinates)
          (assoc :geo_coordinates nil)))
    data))

(defn get-by-id [db id]
  (when-let [result (db.i/execute-one! db {:select select-columns
                                           :from [:collection]
                                           :where [:= :id id]})]
    (store.i/coerce spec/Collection result)))

(defn get-by-accession-id [db accession-id]
  (when-let [result (db.i/execute-one! db {:select select-columns
                                           :from [:collection]
                                           :where [:= :accession_id accession-id]})]
    (store.i/coerce spec/Collection result)))

(defn create! [db data]
  (let [data (->> data
                  (store.i/coerce spec/CreateCollection)
                  (store.i/encode spec/CreateCollection)
                  prepare-insert-data)]
    (when-let [result (db.i/execute-one! db {:insert-into [:collection]
                                             :values [data]
                                             :returning [:id]})]
      (get-by-id db (:collection/id result)))))

(defn update! [db id data]
  (let [data (->> data
                  (store.i/coerce spec/UpdateCollection)
                  (store.i/encode spec/UpdateCollection)
                  prepare-update-data)]
    (when-let [_result (db.i/execute-one! db {:update :collection
                                              :set data
                                              :where [:= :id id]}
                                          {:returning-keys 1})]
      (get-by-id db id))))

(create-ns 'sepal.collection.interface)
(alias 'coll.i 'sepal.collection.interface)

(defn factory [{:keys [db accession] :as args}]
  (let [data (-> (mg/generate spec/CreateCollection)
                 (assoc :accession-id (:accession/id accession))
                 (dissoc :geo-coordinates))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::coll.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :collection {:id (:collection/id data)}))))
