(ns sepal.media.core
  (:require [camel-snake-kebab.core :as csk]
            [integrant.core :as ig]
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

(defn- linked-to [resource-type ids]
  [:and [:= :ml.resource_type resource-type] [:in :ml.resource_id ids]])

(defn- scope-clause
  "Which links a Media tab shows. :direct is links to the record itself.
  :below adds links to what sits under it: an accession's material, and for a
  taxon every descendant taxon, their accessions and those accessions'
  material. For a location, :below adds the material stored there."
  [resource-type resource-id scope]
  (let [direct [:and
                [:= :ml.resource_type resource-type]
                [:= :ml.resource_id resource-id]]]
    (if (not= scope :below)
      direct
      (case resource-type
        "accession"
        [:or direct
         (linked-to "material" {:select [:id] :from [:material]
                                :where [:= :accession_id resource-id]})]

        "taxon"
        (let [descendants {:select [:id] :from [:descendant]}
              accessions {:select [:id] :from [:accession]
                          :where [:in :taxon_id descendants]}]
          [:or
           (linked-to "taxon" descendants)
           (linked-to "accession" accessions)
           (linked-to "material" {:select [:id] :from [:material]
                                  :where [:in :accession_id accessions]})])

        "location"
        [:or direct
         (linked-to "material" {:select [:id] :from [:material]
                                :where [:= :location_id resource-id]})]

        direct))))

(defn get-linked
  "Media linked to a record, newest first, each carrying the link it came
  through as :media/link. See `scope-clause` for :scope."
  [db resource-type resource-id opts]
  (let [{:keys [scope] :as opts-map} (apply hash-map opts)
        descendant-cte (when (and (= scope :below) (= resource-type "taxon"))
                         {:with-recursive
                          [[[:descendant {:columns [:id]}]
                            {:union-all [{:select [:id] :from [:taxon]
                                          :where [:= :id resource-id]}
                                         {:select [:t.id] :from [[:taxon :t]]
                                          :join [:descendant [:= :t.parent_id :descendant.id]]}]}]]})]
    (some->> (merge descendant-cte
                    {:select [:m.*
                              [:ml.resource_type :link_resource_type]
                              [:ml.resource_id :link_resource_id]]
                     :from [[:media :m]]
                     :join [[:media_link :ml]
                            [:= :ml.media_id :m.id]]
                     :where (scope-clause resource-type resource-id scope)
                     :order-by [[:m.created_at :desc] [:m.id :desc]]}
                    (dissoc opts-map :scope))
             (db.i/execute! db)
             (mapv (fn [row]
                     (assoc (store.i/coerce spec/Media row)
                            :media/link {:resource-type (:media-link/link-resource-type row)
                                         :resource-id (:media-link/link-resource-id row)}))))))

(defn create! [db data]
  (store.i/create! db :media data spec/CreateMedia spec/Media))

(defn update! [db id data]
  (store.i/update! db :media id data spec/UpdateMedia spec/Media))

(defn delete! [db id]
  (jdbc.sql/delete! db :media {:id id}))

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
                               {:insert-into [:media_link :ml]
                                :values [data]
                                :on-conflict [:media_id]
                                :do-update-set {:fields [:resource_type :resource_id]
                                                :where [:= :ml.media_id media-id]}
                                :returning [:*]})]
      (get-link db media-id))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn unlink! [db media-id]
  (db.i/execute-one! db {:delete-from :media_link
                         :where [:= :media-id media-id]}))

(defn unlink-resource!
  "Every media link on one resource. The media objects themselves are not
  touched -- a media object is a record in its own right."
  [db resource-type resource-id]
  (db.i/execute-one! db {:delete-from :media_link
                         :where [:and
                                 [:= :resource_type (csk/->kebab-case-string resource-type)]
                                 [:= :resource_id resource-id]]})
  nil)

(defn total-size-in-bytes
  "Sum of every media row's size. A sum over no rows is nil in SQLite, so an empty
  garden reports 0 — callers count bytes and cannot use a nil."
  [db]
  (or (:total (db.i/execute-one! db {:select [[[:sum :size_in_bytes] :total]]
                                     :from [:media]}))
      0))

(create-ns 'sepal.media.interface)
(alias 'media.i 'sepal.media.interface)

(defn factory [{:keys [db user] :as args}]
  (let [data (-> (mg/generate spec/CreateMedia)
                 (assoc :created-by (:user/id user))
                 (merge (m/decode spec/CreateMedia args (mt/strip-extra-keys-transformer))))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::media.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :media {:id (:media/id data)}))))
