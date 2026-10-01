(ns sepal.location.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.location.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :location id spec/Location))

(defn subtree
  "A subquery selecting the id of every location matching `pred`, over
  `location l`, and of every location below one.

  UNION rather than UNION ALL: a text filter can match a parent and its child,
  and each id should appear once. It also ends the recursion if a cycle ever
  reached the table without going through update!."
  [pred]
  {:with-recursive [[[:subtree {:columns [:id]}]
                     {:union [{:select [:l.id] :from [[:location :l]] :where pred}
                              {:select [:c.id]
                               :from [[:location :c]]
                               :join [[:subtree :s] [:= :c.parent_id :s.id]]}]}]]
   :select [:id]
   :from [:subtree]})

(defn paths
  "{id [location ...]} for each of `ids` that exists, root first and ending
  with the location itself. One query for the whole set, so a list page asks
  once rather than once per row."
  [db ids]
  (if (empty? ids)
    {}
    (let [by-id (->> (db.i/execute!
                       db {:with-recursive [[[:up {:columns [:id]}]
                                             {:union [{:select [:id] :from [:location]
                                                       :where [:in :id (vec ids)]}
                                                      {:select [:l.parent_id]
                                                       :from [[:location :l]]
                                                       :join [[:up :u] [:= :l.id :u.id]]
                                                       :where [:not= :l.parent_id nil]}]}]]
                           :select [:l.id :l.code :l.name :l.parent_id]
                           :from [[:location :l]]
                           :join [[:up :u] [:= :u.id :l.id]]})
                     (map #(select-keys % [:location/id :location/code
                                           :location/name :location/parent-id]))
                     (into {} (map (juxt :location/id identity))))
          ;; Walks up with a seen set, so a cycle ends the walk instead of
          ;; looping.
          chain (fn [id]
                  (loop [id id seen #{} acc ()]
                    (if-let [loc (and (not (seen id)) (by-id id))]
                      (recur (:location/parent-id loc) (conj seen id) (conj acc loc))
                      (vec acc))))]
      (into {} (for [id ids :when (by-id id)] [id (chain id)])))))

(defn count-children
  "Direct children of this location, or only those with `status`."
  ([db id]
   (db.i/count db {:select [:id] :from [:location] :where [:= :parent_id id]}))
  ([db id status]
   (db.i/count db {:select [:id] :from [:location]
                   :where [:and [:= :parent_id id] [:= :status (name status)]]})))

(defn list-children
  "Direct children of this location, by code."
  [db id]
  (->> (db.i/execute! db {:select [:*] :from [:location]
                          :where [:= :parent_id id]
                          :order-by [[:code :asc]]})
       (mapv #(store.i/coerce spec/Location %))))

(defn- refuse-archived-parent!
  "An active location never sits under an archived one, so an archived
  location takes no new sub-locations."
  [db parent-id]
  (when (= "archived" (:location/status
                        (db.i/execute-one! db {:select [:status] :from [:location]
                                               :where [:= :id parent-id]})))
    (throw (ex-info "A location can't sit inside an archived one"
                    {:reason ::archived-parent :parent-id parent-id}))))

(defn create!
  "Refuses an archived parent. Checked here rather than in the route, so the
  import loader gets it too."
  [db data]
  (when-let [parent-id (:parent-id data)]
    (refuse-archived-parent! db parent-id))
  (store.i/create! db :location data spec/CreateLocation spec/Location))

(defn- inside-itself? [db id parent-id]
  (some? (db.i/execute-one! db {:select [:id] :from [:location]
                                :where [:and [:= :id parent-id]
                                        [:in :id (subtree [:= :l.id id])]]})))

(defn update!
  "Refuses a parent that is the location itself, anything below it, or
  archived. Checked here rather than in the route, so the import loader gets
  it too."
  [db id data]
  (when-let [parent-id (:parent-id data)]
    (when (inside-itself? db id parent-id)
      (throw (ex-info "A location can't sit inside itself"
                      {:reason ::cycle :id id :parent-id parent-id})))
    (refuse-archived-parent! db parent-id))
  (store.i/update! db :location id data spec/UpdateLocation spec/Location))

(defn set-status! [db id status]
  (update! db id {:status status}))

(defn delete! [db id]
  (jdbc.sql/delete! db :location {:id id})
  nil)

(create-ns 'sepal.location.interface)
(alias 'loc.i 'sepal.location.interface)

(defonce ^:private factory-code-seq (atom 0))

(defn factory [{:keys [db parent data] :as args}]
  (let [values (-> (mg/generate spec/CreateLocation)
                   ;; A location code is unique in the garden, and the spec asks
                   ;; only for two characters -- generated ones collide often
                   ;; enough to fail a suite at random, on whichever test drew
                   ;; second.
                   (assoc :code (format "L%05d" (swap! factory-code-seq inc)))
                   (assoc :parent-id (:location/id parent))
                   (merge data))
        result (create! db values)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::loc.i/factory [_ {:location/keys [id] :as data}]
  (when id
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :location {:id id}))))
