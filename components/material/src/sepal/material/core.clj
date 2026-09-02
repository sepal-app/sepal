(ns sepal.material.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.material.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :material id spec/Material))

(defn create! [db data]
  (store.i/create! db :material data spec/CreateMaterial spec/Material))

(defn create-change!
  "Record a history row directly. The import path uses this; interactive
  writes go through `update!`."
  [db data]
  (store.i/create! db :material-change data spec/CreateMaterialChange
                   spec/MaterialChange))

(defn list-reasons
  "Every material_change_reason, ordered by code."
  [db]
  (db.i/execute! db {:select [:*]
                     :from [:material-change-reason]
                     :order-by [[:code :asc]]}))

(defn list-by-material-id
  "A material's change history, newest first, each row carrying the reason
  label."
  [db material-id]
  (db.i/execute! db {:select [:mc.* :mcr.label]
                     :from [[:material-change :mc]]
                     :left-join [[:material-change-reason :mcr]
                                 [:= :mc.reason :mcr.code]]
                     :where [:= :mc.material_id material-id]
                     :order-by [[:mc.changed_at :desc] [:mc.id :desc]]}))

(defn moved-out-by-location-id
  "Change rows whose material left this location, most recent first, with the
  material code and destination name."
  [db location-id]
  (db.i/execute! db {:select [:mc.* :m.code :l.name]
                     :from [[:material-change :mc]]
                     :join [[:material :m] [:= :mc.material_id :m.id]]
                     :left-join [[:location :l] [:= :mc.to_location_id :l.id]]
                     :where [:= :mc.from_location_id location-id]
                     :order-by [[:mc.changed_at :desc] [:mc.id :desc]]}))

(defn- move? [current new]
  (not= (:material/location-id current) (:material/location-id new)))

(defn- write-update! [db id data]
  (let [{:keys [reason]} data
        current (store.i/get-by-id db :material id spec/Material)
        new (store.i/update! db :material id (dissoc data :reason)
                             spec/UpdateMaterial spec/Material)]
    (when (and current new
               (or (move? current new)
                   (not= (:material/quantity current)
                         (:material/quantity new))))
      (create-change! db {:material-id id
                          :from-location-id (when (move? current new)
                                              (:material/location-id current))
                          :to-location-id (when (move? current new)
                                            (:material/location-id new))
                          :quantity (- (:material/quantity new)
                                       (:material/quantity current))
                          :reason (when (seq reason) reason)}))
    new))

(defn update! [db id data]
  ;; The update and its history row are one transaction. When the caller is
  ;; already inside one, join it rather than start a nested transaction —
  ;; SQLite's driver commits the outer transaction on setAutoCommit(false).
  (if (jdbc/active-tx?)
    (write-update! db id data)
    (db.i/with-transaction [tx db]
      (write-update! tx id data))))

(defn count-by-accession-id
  "Count materials for a given accession."
  [db accession-id]
  (db.i/count db {:select [:id]
                  :from [:material]
                  :where [:= :accession_id accession-id]}))

(defn count-by-location-id
  "Count materials at a given location."
  [db location-id]
  (db.i/count db {:select [:id]
                  :from [:material]
                  :where [:= :location_id location-id]}))

(defn count-by-taxon-id
  "Count materials for a given taxon (via accession)."
  [db taxon-id]
  (db.i/count db {:select [:m.id]
                  :from [[:material :m]]
                  :join [[:accession :a] [:= :m.accession_id :a.id]]
                  :where [:= :a.taxon_id taxon-id]}))

(defn count-all
  "Count every material in the garden."
  [db]
  (db.i/count db {:select [:id]
                  :from [:material]}))

(create-ns 'sepal.material.interface)
(alias 'mat.i 'sepal.material.interface)

(defn factory [{:keys [db accession location] :as args}]
  (let [data (-> (mg/generate spec/CreateMaterial)
                 (assoc :accession-id (:accession/id accession))
                 (assoc :location-id (:location/id location)))
        ;; The schema CHECK forbids a positive quantity on a non-current lot
        ;; (dead, transferred, other), which the generator otherwise produces.
        data (if (contains? #{:alive :dormant :unknown} (:status data))
               data
               (assoc data :quantity 0))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::mat.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :material {:id (:material/id data)}))))
