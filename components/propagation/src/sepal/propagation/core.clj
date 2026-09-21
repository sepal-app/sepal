(ns sepal.propagation.core
  (:require [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.propagation.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :propagation id spec/Propagation))

(defn create! [db data]
  (store.i/create! db :propagation data spec/CreatePropagation spec/Propagation))

(defn update! [db id data]
  (store.i/update! db :propagation id data spec/UpdatePropagation spec/Propagation))

(defn delete! [db id]
  (jdbc.sql/delete! db :propagation {:id id})
  nil)

(defn list-types
  "Every propagation_type, ordered by label. `clonal` rides along: the form
  reads it to decide which product to offer first."
  [db]
  (db.i/execute! db {:select [:*]
                     :from [:propagation-type]
                     :order-by [[:label :asc]]}))

(defn list-statuses [db]
  (db.i/execute! db {:select [:*]
                     :from [:propagation-status]
                     :order-by [[:name :asc]]}))

(defn list-by-parent-accession-id
  "Propagations taken from this accession, newest first."
  [db accession-id]
  (db.i/execute! db {:select [:*]
                     :from [:propagation]
                     :where [:= :parent_accession_id accession-id]
                     :order-by [[:propagated_on :desc] [:id :desc]]}))

(defn list-by-parent-material-id
  "Propagations taken from this individual plant, newest first."
  [db material-id]
  (db.i/execute! db {:select [:*]
                     :from [:propagation]
                     :where [:= :parent_material_id material-id]
                     :order-by [[:propagated_on :desc] [:id :desc]]}))

(defn list-by-location-id
  "Propagations running at this location. The location panel shows what is on
  a bench alongside the material filed there."
  [db location-id]
  (db.i/execute! db {:select [:*]
                     :from [:propagation]
                     :where [:= :location_id location-id]
                     :order-by [[:propagated_on :desc] [:id :desc]]}))

;; The counts the delete path asks for. A propagation is history like a
;; material_change, so a parent, bench or rootstock it names cannot be deleted
;; while it exists.

(defn count-by-parent-accession-id [db accession-id]
  (db.i/count db {:select [:id]
                  :from [:propagation]
                  :where [:= :parent_accession_id accession-id]}))

(defn count-by-parent-material-id [db material-id]
  (db.i/count db {:select [:id]
                  :from [:propagation]
                  :where [:= :parent_material_id material-id]}))

(defn count-by-rootstock-taxon-id [db taxon-id]
  (db.i/count db {:select [:id]
                  :from [:propagation]
                  :where [:= :rootstock_taxon_id taxon-id]}))

(defn count-by-location-id [db location-id]
  (db.i/count db {:select [:id]
                  :from [:propagation]
                  :where [:= :location_id location-id]}))

(create-ns 'sepal.propagation.interface)
(alias 'prop.i 'sepal.propagation.interface)

(defn factory
  "Build a propagation for tests.

  The parent accession is passed in, the way material's factory takes its
  accession and location, because a propagation above nothing is not a record.
  `:data` overrides the generated type and counts when a test needs a
  particular one."
  [{:keys [db accession data] :as args}]
  (let [result (create! db (merge {:type :cutting
                                   :parent-accession-id (:accession/id accession)}
                                  data))]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::prop.i/factory [_ {:propagation/keys [id] :as data}]
  (when id
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :propagation {:id id}))))
