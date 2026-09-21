(ns sepal.observation.core
  (:require [camel-snake-kebab.core :as csk]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.observation.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(def ^:private own-keys
  [:observation/id :observation/resource-type :observation/resource-id
   :observation/type :observation/value :observation/observed-on
   :observation/observed-by :observation/next-check-on :observation/note
   :observation/created-by :observation/created-at])

(defn observer
  "Who to credit for an observation: `:observation/observed-by` when it's
  set, else the creating user's email, so a volunteer with no user account
  still names someone and a row with neither renders nothing."
  [observation]
  (or (:observation/observed-by observation) (:observation/author-email observation)))

(defn- row->observation
  "A joined row carries the author's email and the two lookup labels alongside
  the observation's own columns. Coerce the observation fields on their own --
  the spec is closed -- then hang the rest off the result.

  The two label columns come back under :observation-type/label and
  :observation-value/label. A `/`, used the way the schema doc first tried it,
  is not a legal SQL identifier character and fails to parse; a `__` is what
  the result-set builder's label-fn (sepal.database.core) already treats as a
  namespace separator, so that is the alias base-query actually uses."
  [row]
  (let [observation (-> (store.i/coerce spec/Observation (select-keys row own-keys))
                        (assoc :observation/author-email (:user/email row)
                               :observation/type-label (:observation-type/label row)
                               :observation/value-label (:observation-value/label row)))]
    (assoc observation :observation/observer (observer observation))))

(def ^:private base-query
  "Every read joins the same three tables. `value` is nullable, so
  observation_value is a LEFT JOIN -- an inner one would drop every general
  observation."
  {:select [:o.* :u.email
            [:t.label :observation_type__label]
            [:v.label :observation_value__label]]
   :from [[:observation :o]]
   :left-join [[:user :u] [:= :u.id :o.created_by]
               [:observation_type :t] [:= :t.code :o.type]
               [:observation_value :v] [:and
                                        [:= :v.type :o.type]
                                        [:= :v.code :o.value]]]})

(defn get-by-id
  "One observation, joined the same way `get-for-resource` is so the fallback
  in `:observation/observer` works from either. nil when id doesn't match a
  row."
  [db id]
  (some-> (db.i/execute-one! db (assoc base-query :where [:= :o.id id]))
          row->observation))

(defn get-for-resource
  "A resource's observations, newest observed first.

  Ordered by observed_on then id. Two observations on the same day are a
  normal thing to record, and id is what breaks the tie -- created_at cannot,
  because SQLite's datetime('now') has one-second resolution."
  [db resource-type resource-id]
  (->> (db.i/execute! db (assoc base-query
                                :where [:and
                                        [:= :o.resource_type (csk/->kebab-case-string resource-type)]
                                        [:= :o.resource_id resource-id]]
                                :order-by [[:o.observed_on :desc] [:o.id :desc]]))
       (mapv row->observation)))

(defn count-for-resource
  "How many observations this resource has."
  [db resource-type resource-id]
  (db.i/count db {:select [:id]
                  :from [:observation]
                  :where [:and
                          [:= :resource_type (csk/->kebab-case-string resource-type)]
                          [:= :resource_id resource-id]]}))

(defn due
  "Observations whose next check has arrived, oldest first.

  The `not null` predicate is not redundant. A null next_check_on means nobody
  asked to be reminded, and SQLite's `<=` against null yields null rather than
  false -- which the WHERE clause treats as not-matching, but only by accident
  of three-valued logic. Saying it is what makes the intent readable."
  [db on-date]
  (->> (db.i/execute! db (assoc base-query
                                :where [:and
                                        [:not= :o.next_check_on nil]
                                        [:<= :o.next_check_on on-date]]
                                :order-by [[:o.next_check_on :asc] [:o.id :asc]]))
       (mapv row->observation)))

(defn count-due
  "How many observations are due, without materialising them -- see `due`."
  [db on-date]
  (db.i/count db {:select [:id]
                  :from [:observation]
                  :where [:and
                          [:not= :next_check_on nil]
                          [:<= :next_check_on on-date]]}))

(defn list-types
  "Every observation_type, ordered by code."
  [db]
  (db.i/execute! db {:select [:*]
                     :from [:observation-type]
                     :order-by [[:code :asc]]}))

(defn list-values
  "Every observation_value, ordered by type then code."
  [db]
  (db.i/execute! db {:select [:*]
                     :from [:observation-value]
                     :order-by [[:type :asc] [:code :asc]]}))

(defn create! [db data]
  (store.i/create! db :observation data spec/CreateObservation spec/Observation))

(defn update! [db id data]
  (store.i/update! db :observation id data spec/UpdateObservation spec/Observation))

(defn delete! [db id]
  (jdbc.sql/delete! db :observation {:id id})
  nil)

(defn delete-for-resource!
  "Every observation on one resource. The polymorphic resource_id carries no
  foreign key, so this is the cascade."
  [db resource-type resource-id]
  (db.i/execute-one! db {:delete-from :observation
                         :where [:and
                                 [:= :resource_type (csk/->kebab-case-string resource-type)]
                                 [:= :resource_id resource-id]]})
  nil)

(create-ns 'sepal.observation.interface)
(alias 'observation.i 'sepal.observation.interface)

(defn factory
  "Build an observation for tests. `:data` overrides any generated field."
  [{:keys [db resource-type resource-id user data] :as _args}]
  (let [result (create! db (merge {:resource-type resource-type
                                   :resource-id resource-id
                                   :type "general"
                                   :observed-on "2026-01-01"
                                   :note "a test observation"
                                   :created-by (:user/id user)}
                                  data))]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::observation.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :observation {:id (:observation/id data)}))))
