(ns sepal.accession.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface.spec :as spec]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :accession id spec/Accession))

(defn create! [db data]
  (store.i/create! db :accession data spec/CreateAccession spec/Accession))

(defn update! [db id data]
  (store.i/update! db :accession id data spec/UpdateAccession spec/Accession))

(defn count-by-taxon-id
  "Count accessions for a given taxon."
  [db taxon-id]
  (db.i/count db {:select [:id]
                  :from [:accession]
                  :where [:= :taxon_id taxon-id]}))

(defn count-by-supplier-contact-id
  "Count accessions for a given supplier contact."
  [db contact-id]
  (db.i/count db {:select [:id]
                  :from [:accession]
                  :where [:= :supplier_contact_id contact-id]}))

(create-ns 'sepal.accession.interface)
(alias 'acc.i 'sepal.accession.interface)

(defn factory [{:keys [db taxon contact] :as args}]
  (let [data (-> (mg/generate spec/CreateAccession)
                 (assoc :taxon-id (:taxon/id taxon))
                 (assoc :supplier-contact-id (when contact (:contact/id contact))))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::acc.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :accession {:id (:accession/id data)}))))
