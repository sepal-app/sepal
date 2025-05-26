(ns sepal.media.core
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.experimental.time.generator]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.media.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :media id spec/Media))

(defn get-link [db media-id]
  (some->> {:select :*
            :from :media_link
            :where [:= :media_id media-id]}
           (db.i/execute-one! db)
           (store.i/coerce spec/MediaLink)))

(defn get-linked [db resource-type resource-id opts]
  (let [opts-map (apply hash-map opts)]
    (some->> {:select :m.*
              :from [[:public.media :m]]
              :join [[:media_link :ml]
                     [:= :ml.media_id :m.id]]
              :where [:and
                      [:= :ml.resource_type resource-type]
                      [:= :ml.resource_id resource-id]]}
             (merge opts-map)
             (db.i/execute! db)
             (mapv #(store.i/coerce spec/Media %)))))

(defn create! [db data]
  (store.i/create! db :media data spec/CreateMedia spec/Media))

(defn delete! [db id]
  (jdbc.sql/delete! db :public.media {:id id}))

(defn link! [db media-id resource-id resource-type]
  (try
    ;; First we db/coerce the data into a CreateMediaLink to make sure it
    ;; validates and then we db/encode the data so its in the form expected by
    ;; the database
    (let [data (->> {:media-id media-id
                     :resource-id resource-id
                     :resource-type resource-type}
                    (store.i/coerce spec/CreateMediaLink)
                    (store.i/encode spec/CreateMediaLink))
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

(defn factory [{:keys [db created-by] :as args}]
  (let [data (-> (mg/generate spec/CreateMedia)
                 (merge (m/decode spec/CreateMedia args (mt/strip-extra-keys-transformer)))
                 (assoc :created-by (:user/id created-by)))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::media.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :media {:id (:media/id data)}))))
