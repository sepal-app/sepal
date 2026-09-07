(ns sepal.tag.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.store.interface :as store.i]
            [sepal.tag.interface.spec :as spec]))

(defn available?
  "Whether this database has `tag` and `tag_link` at all: false on a database
  below the migration that added them, where `select` on either table is a
  hard error rather than an empty result."
  [ctx]
  (db.i/at-least-version? ctx (db.i/tag-version)))

;; Every read below takes `ctx` first and gates on `available?`, the shape
;; components/synonym uses. A `select` against `tag` on a database below the
;; migration is a hard SQLite error, not an empty result, so the gate has to
;; happen before the query -- and putting it here rather than at each handler
;; means a call site added later cannot forget it.

(defn get-by-id [ctx db id]
  (when (available? ctx)
    (store.i/get-by-id db :tag id spec/Tag)))

(defn get-by-name
  "Case-insensitive by the column's own collation -- `tag.name` is
  `collate nocase`, so `=` here already matches regardless of case."
  [ctx db name]
  (when (available? ctx)
    (some->> {:select :*
              :from :tag
              :where [:= :name name]}
             (db.i/execute-one! db)
             (store.i/coerce spec/Tag))))

(defn list-all
  "Every tag with its link count, including a tag with none."
  [ctx db]
  (when (available? ctx)
    (db.i/execute! db {:select [:t.* [[:count :tl.id] :tag__link_count]]
                       :from [[:tag :t]]
                       :left-join [[:tag_link :tl] [:= :tl.tag_id :t.id]]
                       :group-by [:t.id]
                       :order-by [[:t.name :asc]]})))

(defn create! [db data]
  (store.i/create! db :tag data spec/CreateTag spec/Tag))

(defn update! [db id data]
  (store.i/update! db :tag id data spec/UpdateTag spec/Tag))

(defn delete!
  "Remove the tag and its links in one transaction. A note's polymorphic
  resource_id has no foreign key to hang this off, but tag_link.tag_id does,
  so here the links can be cleaned up with the row they belong to."
  [db id]
  (db.i/with-transaction [tx db]
    (jdbc.sql/delete! tx :tag_link {:tag_id id})
    (jdbc.sql/delete! tx :tag {:id id}))
  nil)

(defn tag!
  "Link `tag-id` to a resource. Idempotent: tagging the same resource twice
  with the same tag hits tag_link_unique_idx, and that unique-constraint
  failure is swallowed rather than surfaced -- the end state the caller wanted
  (this resource carries this tag) is already true.

  Returns `true` when a link row was actually inserted, `false` when the link
  already existed (the swallowed-conflict case above, a true no-op), or an
  error map for any other failure. Callers use this to decide whether to
  record a `linked` activity event -- one is only warranted for a real
  state change."
  [db tag-id resource-id resource-type]
  (let [data (->> {:tag-id tag-id :resource-id resource-id :resource-type resource-type}
                  (store.i/coerce spec/CreateTagLink)
                  (store.i/encode spec/CreateTagLink))]
    (try
      (db.i/execute-one! db {:insert-into [:tag_link] :values [data]})
      true
      (catch org.sqlite.SQLiteException ex
        (if (re-find #"UNIQUE constraint failed" (ex-message ex))
          false
          (error.i/ex->error ex))))))

(defn untag!
  "Unlink `tag-id` from a resource. Returns `true` when a link row was
  actually deleted, `false` when no such link existed (a true no-op -- a
  stale id, a double-DELETE, or a tag id belonging to a different resource).
  Callers use this to decide whether to record an `unlinked` activity event."
  [db tag-id resource-id resource-type]
  (-> (jdbc.sql/delete! db :tag_link {:tag_id tag-id
                                      :resource_id resource-id
                                      :resource_type (name resource-type)})
      :next.jdbc/update-count
      pos?))

(defn get-for-resource
  "Tags on one resource, alphabetical."
  [ctx db resource-type resource-id]
  (when (available? ctx)
    (db.i/execute! db {:select [:t.*]
                       :from [[:tag :t]]
                       :join [[:tag_link :tl] [:= :tl.tag_id :t.id]]
                       :where [:and
                               [:= :tl.resource_type (name resource-type)]
                               [:= :tl.resource_id resource-id]]
                       :order-by [[:t.name :asc]]})))

(defn get-tagged
  "Every link row for one tag. For browsing a tag's linked records from the
  tag index, which nothing does yet -- the index shows counts only."
  [ctx db tag-id]
  (when (available? ctx)
    (db.i/execute! db {:select [:*]
                       :from [:tag_link]
                       :where [:= :tag_id tag-id]})))

(create-ns 'sepal.tag.interface)
(alias 'tag.i 'sepal.tag.interface)

(defn factory [{:keys [db] :as _args}]
  (let [data (mg/generate spec/CreateTag)
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::tag.i/factory [_ {:tag/keys [id] :as data}]
  (when id
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :tag_link {:tag_id id})
      (jdbc.sql/delete! db :tag {:id id}))))
