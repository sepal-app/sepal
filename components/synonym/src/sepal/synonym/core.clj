(ns sepal.synonym.core
  (:require [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]
            [sepal.synonym.interface.spec :as spec]))

(defn- available?
  "Whether this database has `taxon_synonym` at all."
  [ctx]
  (db.i/at-least-version? ctx (db.i/taxon-synonym-version)))

(def ^:private columns
  "`taxon_synonym` columns, aliased onto the `:synonym/...` namespace the spec
  uses. `taxon_synonym` does not share a name with the `synonym` interface
  namespace, so the result set's default table-derived namespace
  (`:taxon-synonym/...`) doesn't match `spec/Synonym`. The `x__y` alias shape
  is what `sepal.database.core/label-fn` splits into `:x/y`; a plain
  `:ns/name` qualified keyword renders as `ns.name`, which SQLite rejects as
  an alias."
  [[:id :synonym__id]
   [:taxon_id :synonym__taxon_id]
   [:synonym_name :synonym__synonym_name]
   [:source :synonym__source]
   [:created_by :synonym__created_by]
   [:created_at :synonym__created_at]])

(defn add-synonym!
  "Insert a synonym row.

  Not `store.i/create!`: see `columns`. The returned row is still coerced
  through `spec/Synonym` by hand, same as `store.i/create!` would, so a typo in
  `columns` fails loudly instead of silently mislabeling a column."
  [db data]
  (let [data (->> data
                  (store.i/coerce spec/CreateSynonym)
                  (store.i/encode spec/CreateSynonym))]
    (->> (db.i/execute-one! db {:insert-into [:taxon_synonym]
                                :values [data]
                                :returning columns})
         (store.i/coerce spec/Synonym))))

(defn remove-synonym! [db id]
  (jdbc.sql/delete! db :taxon_synonym {:id id})
  nil)

(defn list-for-taxon
  "The garden's own synonyms for a taxon, alphabetical by name.

  Returns empty on a database below the migration that added the table rather
  than failing the request."
  [ctx db taxon-id]
  (if-not (available? ctx)
    []
    (db.i/execute! db {:select columns
                       :from [:taxon_synonym]
                       :where [:= :taxon_id taxon-id]
                       :order-by [[:synonym_name :asc]]})))

(create-ns 'sepal.synonym.interface)
(alias 'synonym.i 'sepal.synonym.interface)

(defn factory [{:keys [db taxon] :as _args}]
  (let [data (-> (mg/generate spec/CreateSynonym)
                 (dissoc :created-by)
                 (assoc :taxon-id (:taxon/id taxon)))
        result (add-synonym! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::synonym.i/factory [_ {:synonym/keys [id] :as data}]
  (when id
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :taxon_synonym {:id id}))))
