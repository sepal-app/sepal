(ns sepal.taxon.core
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface.spec :as spec]))

(defn get-by-id [db id]
  (let [result (jdbc.sql/get-by-id db :taxon id)]
    (when (some? result)
      (db.i/coerce spec/Taxon result))))

(defn create! [db data]
  (try
    ;; First we db/coerce the data into a CreateTaxon to make sure it
    ;; validates and then we db/encode the data so its in the form expected by
    ;; the database
    (let [data (->> data
                    (db.i/coerce spec/CreateTaxon)
                    (db.i/encode spec/CreateTaxon))
          result (jdbc.sql/insert! db :taxon data {:return-keys true})]
      (db.i/coerce spec/Taxon result))
    (catch Exception ex
      (error.i/ex->error ex))))

(comment
  ;; id
  ;; alternative_id
  ;; basionym_id
  ;; scientific_name
  ;; authorship
  ;; rank
  ;; uninomial
  ;; genus
  ;; infrageneric_epithet
  ;; specific_epithet
  ;; infraspecific_epithet
  ;; code
  ;; reference_id
  ;; published_in_year
  ;; link
  ())

(defn update! [db id data]
  (try
    (let [data (->> data
                    (db.i/coerce spec/UpdateTaxon)
                    (db.i/encode spec/UpdateTaxon))
          result (jdbc.sql/update! db
                                   :taxon
                                   data
                                   {:id id}
                                   {:return-keys true})]
      ;; The result will be nil if the row wasn't updated
      (when (some? result)
        (db.i/coerce spec/Taxon result)))
    (catch Exception ex
      (error.i/ex->error ex))))

(create-ns 'sepal.taxon.interface)
(alias 'taxon.i 'sepal.taxon.interface)

(defn factory [{:keys [db organization] :as args}]
  ;; TODO: validate the args for required args like organization
  (let [data (-> (mg/generate spec/CreateTaxon)
                 (dissoc :parent-id)
                 (merge (m/decode spec/CreateTaxon args (mt/strip-extra-keys-transformer)))
                 (assoc :organization-id (:organization/id organization)))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::taxon.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :taxon {:id (:taxon/id data)}))))
