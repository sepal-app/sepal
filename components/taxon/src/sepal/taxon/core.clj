(ns sepal.taxon.core
  (:require [clojure.string :as s]
            [integrant.core :as ig]
            [malli.core :as m]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.result-set :as jdbc.rs]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface.spec :as spec]))

(defn get-wfo-name-by-id [db id]
  (db.i/execute-one! db
                     {:select [[[:coalesce :wfo_t.id :wfo_n.id] :id]
                               [:wfo_n.scientific_name :name]
                               [:wfo_n.rank :rank]
                               [:wfo_n.authorship :author]
                               [:wfo_n.id :wfo_plantlist_name_id]
                               [:wfo_t.parent_id :parent_id]]
                      :from [[:wfo_plantlist_2023_12.name :wfo_n]]
                      :join [[:wfo_plantlist_2023_12.taxon :wfo_t]
                             [:= :wfo_t.name_id :wfo_n.id]]
                      :where [:or
                              [:= :wfo_n.id id]
                              [:= :wfo_t.id id]]}
                     {:builder-fn jdbc.rs/as-unqualified-kebab-maps}))

(defn wfo-id? [id]
  (s/starts-with? id "wfo"))

(defn get-by-id [db id]
  (let [result (if (wfo-id? id)
                 (-> (get-wfo-name-by-id db id)
                     (assoc :organization-id nil)
                   ;; namespace the keys with :taxon so that data returned from this
                   ;; loader has consistent same key
                     (update-keys #(keyword "taxon" (name %))))
                 (jdbc.sql/get-by-id db :taxon id))]
    (when (some? result)
      (db.i/coerce spec/Taxon result))))

(defn create! [db data]
  ;; TODO: Create auditing event
  (tap> (str "taxon/create!:" data))
  (try
    ;; TODO: first we db/coerce the data into a CreateTaxon to make sure it
    ;; validates and then we db/encode the data so its in the form expected by
    ;; the database
    (let [data (db.i/coerce spec/CreateTaxon data)
          result  (jdbc.sql/insert! db
                                    :taxon
                                    (db.i/encode spec/CreateTaxon data)
                                    {:return-keys true})]
      (db.i/coerce spec/Taxon result))
    (catch Exception ex
      (tap> ex)
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
  ;; TODO: validate that if the data if the id is a wfo-id then
  ;; the data needs to have an organization-id
  (try
    (if (wfo-id? id)
      (create! db (assoc data :wfo-plantlist-name-id id))
      (let [data (->> data
                      (db.i/coerce spec/UpdateTaxon)
                      (db.i/encode spec/UpdateTaxon))
            result (jdbc.sql/update! db
                                     :taxon
                                     data
                                     {:id id}
                                     {:return-keys true})]
        (when (some? result)
          (db.i/coerce spec/Taxon result))))
    (catch Exception ex
      (error.i/ex->error ex))))

(create-ns 'sepal.taxon.interface)
(alias 'taxon.i 'sepal.taxon.interface)

(defn factory [{:keys [db organization] :as args}]
  ;; TODO: validate the args for required args like organization
  (let [data (-> (mg/generate spec/CreateTaxon)
                 (merge (m/decode spec/CreateTaxon args (mt/strip-extra-keys-transformer)))
                 (assoc :organization-id (:organization/id organization)))
        _ (tap> (str "data: " data))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::taxon.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :taxon {:id (:taxon/id data)}))))
