(ns sepal.synonym.core
  (:refer-clojure :exclude [resolve])
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]
            [sepal.synonym.interface.spec :as spec]
            [sepal.synonym.reference :as reference]))

(defn available?
  "Whether this database has `taxon_synonym` at all: false on a database below
  the migration that added it, where `select` on the table is a hard error
  rather than an empty result."
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

(defn- local-rows [db taxon-id]
  (db.i/execute! db {:select columns
                     :from [:taxon_synonym]
                     :where [:= :taxon_id taxon-id]
                     :order-by [[:synonym_name :asc]]}))

(defn- local-matches
  "Garden synonym rows whose name contains the query, joined to their taxon.

  A LIKE rather than FTS: this table holds a garden's own handful of rows, not
  WFO's million. `lower()` on the column plus a leading `%` in the pattern
  mean taxon_synonym_name_idx cannot be used here regardless of its `collate
  nocase` — this is a full scan, which is fine at the row counts a single
  garden's synonym table holds."
  [db query]
  (mapv (fn [row]
          {:synonym/synonym-name (:synonym/synonym-name row)
           :synonym/source (:synonym/source row)
           :taxon/id (:taxon/id row)
           :taxon/name (:taxon/name row)})
        (db.i/execute! db {:select [[:s.synonym_name :synonym__synonym_name]
                                    [:s.source :synonym__source]
                                    [:t.id :taxon__id]
                                    [:t.name :taxon__name]]
                           :from [[:taxon_synonym :s]]
                           :join [[:taxon :t] [:= :t.id :s.taxon_id]]
                           :where [:like [:lower :s.synonym_name]
                                   (str "%" (str/lower-case query) "%")]
                           :order-by [[:s.synonym_name :asc]]
                           :limit 50})))

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

(def ^:private core-length
  "A WFO id is 'wfo-0000283538-2025-06', 22 characters; the stable part is the
  first 14. Matching the full string across a release gap resolved 0 of
  1,019,425 rows on 2026-09-02; matching the core resolved 1,014,088."
  14)

(defn- accepted-core [wfo-taxon-id]
  (when (and wfo-taxon-id (>= (count wfo-taxon-id) core-length))
    (subs wfo-taxon-id 0 core-length)))

(defn list-for-taxon
  "The garden's own synonyms for a taxon, plus its WFO synonyms, alphabetical
  local rows first. Returns only local rows on a database below the migration
  that added taxon_synonym, or when the taxon has no wfo_taxon_id, or when the
  process has no WFO reference pool."
  [ctx db taxon-id]
  (let [local (if-not (available? ctx) [] (local-rows db taxon-id))
        ;; Plain column, no alias: `taxon` the table and `taxon` the interface
        ;; namespace agree, so label-fn's table-derived namespace already gives
        ;; :taxon/wfo-taxon-id. An explicit :taxon/wfo-taxon-id alias would
        ;; render as `taxon.wfo-taxon-id`, which SQLite rejects.
        wfo-id (:taxon/wfo-taxon-id
                 (db.i/execute-one! db {:select [:wfo_taxon_id]
                                        :from [:taxon]
                                        :where [:= :id taxon-id]}))
        wfo (if-let [core (accepted-core wfo-id)]
              (mapv (fn [r] {:synonym/synonym-name (:name r)
                             :synonym/source "wfo"
                             :synonym/name-id (:name-id r)})
                    (reference/list-for-accepted-core (:synonym-reference ctx) core))
              [])]
    (into (vec local) wfo)))

(defn resolve
  "Local and WFO synonym matches for a query, each resolved to a garden taxon.

  A WFO hit whose accepted taxon is not in this garden is dropped. An empty
  or nil query yields [] from both halves, agreeing with what
  reference/search already does for its own half — a picker's first
  keystroke is an empty query, and local-matches's LIKE '%%' would otherwise
  return up to 50 arbitrary rows for \"\", and str/lower-case would throw on
  nil."
  [ctx db query]
  (if (empty? query)
    []
    (let [local (if-not (available? ctx) [] (local-matches db query))
          hits (reference/search (:synonym-reference ctx) query)
          cores (distinct (keep :accepted-core hits))
          by-core (when (seq cores)
                    (into {} (map (juxt :taxon/core identity))
                          ;; substr() on the column defeats taxon's index on
                          ;; wfo_taxon_id, so this is a full scan of the taxon
                          ;; table: measured at 50-60 ms per call against the
                          ;; 453,167-row WFO import on 2026-09-02, paid on every
                          ;; keystroke of the picker whenever the FTS half above
                          ;; returned a hit. Left as is deliberately. Fixing it
                          ;; means storing the 14-character core as its own
                          ;; indexed column, which is a migration; if you are
                          ;; here profiling the picker, that is the answer.
                          (db.i/execute! db
                                         {:select [[[:substr :wfo_taxon_id 1 core-length] :taxon__core]
                                                   [:id :taxon__id]
                                                   [:name :taxon__name]]
                                          :from [:taxon]
                                          :where [:in [:substr :wfo_taxon_id 1 core-length] cores]})))]
      (into (vec local)
            (keep (fn [hit]
                    (when-let [taxon (get by-core (:accepted-core hit))]
                      {:synonym/synonym-name (:name hit)
                       :synonym/source "wfo"
                       :taxon/id (:taxon/id taxon)
                       :taxon/name (:taxon/name taxon)}))
                  hits)))))

(def min-query-length
  "The shortest query a synonym search will act on.

  A one-character query is not a search, it is a scan: measured against WFO
  2025-06 on 2026-09-04, `a` resolves to 78,361 distinct accepted taxa and `e`
  to 36,422. Two characters is also what the taxon picker already enforces
  client-side (`bases/app/src/sepal/app/js/taxon-field.ts`), so the two surfaces
  agree on what counts as a query."
  2)

(def max-taxon-ids
  "The most taxa one synonym query will resolve to.

  Two characters bounds the degenerate case but not a broad one: `ca` still
  resolves to 32,003 taxa, `sp` to 10,486, `ro` to 9,072 — all perfectly
  reasonable things to type. 1,000 is 40 pages at 25 rows, and callers are told
  when they hit it. Silent truncation is the defect this replaces."
  1000)

(defn taxon-ids-for-synonym
  "Garden taxon ids whose synonyms match `query`, for use as a search filter.

  Returns {:ids #{…} :truncated? bool :too-short? bool}. `:too-short?` is the
  caller's cue to say why nothing was searched rather than show an empty result.
  `:truncated?` says the answer is a slice, so a caller can say so.

  Unions both halves the way `resolve` does — the garden's own `taxon_synonym`
  rows and the WFO reference — but returns ids rather than rows, because a
  filter narrows a query and does not carry display data. Empty on a database
  below the migration that added `taxon_synonym` and with no reference pool,
  never an error."
  [ctx db query]
  (let [q (str/trim (or query ""))]
    (if (< (count q) min-query-length)
      {:ids #{} :truncated? false :too-short? (seq q)}
      (let [local-ids (if-not (available? ctx)
                        []
                        (map :taxon/id (local-matches db q)))
            ;; One more than the cap, so truncation is observed rather than
            ;; inferred from a full page.
            cores (->> (reference/search (:synonym-reference ctx) q)
                       (keep :accepted-core)
                       distinct
                       (take (inc max-taxon-ids)))
            wfo-ids (when (seq cores)
                      (map :taxon/id
                           (db.i/execute! db
                                          {:select [[:id :taxon__id]]
                                           :from [:taxon]
                                           :where [:in [:substr :wfo_taxon_id 1 core-length]
                                                   (vec cores)]})))
            all (distinct (concat local-ids wfo-ids))]
        {:ids (set (take max-taxon-ids all))
         :truncated? (> (count all) max-taxon-ids)
         :too-short? false}))))

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
