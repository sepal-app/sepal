(ns sepal.taxon.core
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface.spec :as spec]))

(defn get-by-id [db id]
  (store.i/get-by-id db :taxon id spec/Taxon))

(defn list-by-wfo-taxon-id
  "Every taxon carrying this WFO id, as a vector.

  A vector rather than one row because `taxon.wfo_taxon_id` is nullable and
  carries a plain index, not a unique one, so two taxa can hold the same id.
  The importer has to be able to tell that apart from a clean hit -- resolving
  a WFO reference to the wrong taxon is the error nobody would notice."
  [db wfo-taxon-id]
  (->> (db.i/execute! db {:select [:*]
                          :from [:taxon]
                          :where [:= :wfo_taxon_id wfo-taxon-id]})
       (mapv #(store.i/coerce spec/Taxon %))))

(defn create! [db data]
  (let [data (cond-> data
               (not (contains? data :vernacular-names))
               (assoc :vernacular-names []))]
    (store.i/create! db :taxon data spec/CreateTaxon spec/Taxon)))

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
  (store.i/update! db :taxon id data spec/UpdateTaxon spec/Taxon))

(create-ns 'sepal.taxon.interface)
(alias 'taxon.i 'sepal.taxon.interface)

(defn factory [{:keys [db] :as args}]
  (let [data (-> (mg/generate spec/CreateTaxon)
                 (dissoc :parent-id)
                 (merge (m/decode spec/CreateTaxon args (mt/strip-extra-keys-transformer))))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::taxon.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :taxon {:id (:taxon/id data)}))))
