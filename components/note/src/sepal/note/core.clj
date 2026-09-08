(ns sepal.note.core
  (:require [camel-snake-kebab.core :as csk]
            [integrant.core :as ig]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.note.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

(defn get-by-id [db id]
  (store.i/get-by-id db :note id spec/Note))

(defn- row->note
  "A joined row carries the author's email alongside the note's own columns.
  Coerce the note fields on their own — the spec is closed — then hang the
  email off the result."
  [row]
  (-> (store.i/coerce spec/Note
                      (select-keys row [:note/id :note/body :note/resource-type
                                        :note/resource-id :note/created-by
                                        :note/created-at]))
      (assoc :note/author-email (:user/email row))))

(defn get-for-resource
  "A resource's notes, newest first, each carrying its author's email or nil.

  Ordered by id rather than created_at: SQLite's datetime('now') has
  one-second resolution, so two notes written in the same second would come
  back in an arbitrary order."
  [db resource-type resource-id]
  (->> (db.i/execute! db {:select [:n.* :u.email]
                          :from [[:note :n]]
                          :left-join [[:user :u] [:= :u.id :n.created_by]]
                          :where [:and
                                  [:= :n.resource_type (csk/->kebab-case-string resource-type)]
                                  [:= :n.resource_id resource-id]]
                          :order-by [[:n.id :desc]]})
       (mapv row->note)))

(defn count-for-resource
  "How many notes this resource has."
  [db resource-type resource-id]
  (db.i/count db {:select [:id]
                  :from [:note]
                  :where [:and
                          [:= :resource_type (csk/->kebab-case-string resource-type)]
                          [:= :resource_id resource-id]]}))

(defn create! [db data]
  (store.i/create! db :note data spec/CreateNote spec/Note))

(defn update! [db id data]
  (store.i/update! db :note id data spec/UpdateNote spec/Note))

(defn delete! [db id]
  (jdbc.sql/delete! db :note {:id id}))

(create-ns 'sepal.note.interface)
(alias 'note.i 'sepal.note.interface)

(defn factory
  "Build a note for tests. `:data` overrides any generated field."
  [{:keys [db resource-type resource-id user data] :as _args}]
  (let [result (create! db (merge {:body "a test note"
                                   :resource-type resource-type
                                   :resource-id resource-id
                                   :created-by (:user/id user)}
                                  data))]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::note.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :note {:id (:note/id data)}))))
