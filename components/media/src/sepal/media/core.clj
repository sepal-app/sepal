(ns sepal.media.core
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.experimental.time.generator]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface.spec :as spec]
            [sepal.taxon.interface :as taxon.i]))

(defn get-by-id [db id]
  (let [result (jdbc.sql/get-by-id db :media id)]
    (when (some? result)
      (db.i/coerce spec/Media result))))

(defn get-link [db media-id]
  ;; TODO: We could probably get the linked resource data in a single query but
  ;; the naive way is fine for now.
  (when-let [link (some->> {:select :*
                            :from :media_link
                            :where [:= :media_id media-id]}
                           (db.i/execute-one! db)
                           (db.i/coerce spec/MediaLink))]
    (case (:media-link/resource-type link)
      "accession"
      (assoc link
             :media-link/resource
             (accession.i/get-by-id db (:media-link/resource-id link)))
      "taxon"
      (assoc link
             :media-link/resource
             (taxon.i/get-by-id db (:media-link/resource-id link)))
      "location"
      (assoc link
             :media-link/resource
             (location.i/get-by-id db (:media-link/resource-id link)))
      "material"
      (assoc link
             :media-ling/resource
             (material.i/get-by-id db (:media-link/resource-id link))))))

(defn create! [db data]
  (try
    ;; First we db/coerce the data into a CreateMedia to make sure it
    ;; validates and then we db/encode the data so its in the form expected by
    ;; the database
    (let [data (->> data
                    (db.i/coerce spec/CreateMedia)
                    (db.i/encode spec/CreateMedia))
          result (jdbc.sql/insert! db :media data {:return-keys true})]
      (db.i/coerce spec/Media result))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn link! [db media-id resource-id resource-type]
  (try
    ;; First we db/coerce the data into a CreateMediaLink to make sure it
    ;; validates and then we db/encode the data so its in the form expected by
    ;; the database
    (let [data (->> {:media-id media-id
                     :resource-id resource-id
                     :resource-type resource-type}
                    (db.i/coerce spec/CreateMediaLink)
                    (db.i/encode spec/CreateMediaLink))
          _ (db.i/execute-one! db
                               {:insert-into [:public.media_link :ml]
                                :values [data]
                                :on-conflict [:media_id]
                                :do-update-set {:fields [:resource_type :resource_id]
                                                :where [:= :ml.media_id media-id]}
                                :returning [:*]})]
      (get-link db media-id))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn unlink! [db media-id]
  (db.i/execute-one! db {:delete-from :public.media_link
                         :where [:= :media-id media-id]}))

(create-ns 'sepal.media.interface)
(alias 'media.i 'sepal.media.interface)

(defn factory [{:keys [db created-by organization] :as args}]
  ;; TODO: validate the args for required args like organization
  (let [data (-> (mg/generate spec/CreateMedia)
                 (merge (m/decode spec/CreateMedia args (mt/strip-extra-keys-transformer)))
                 (assoc :organization-id (:organization/id organization)
                        :created-by (:user/id created-by)))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::media.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :media {:id (:media/id data)}))))
