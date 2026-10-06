(ns sepal.app.list-query
  "What the visible columns and the sort add to a list's statement. Each
  handler still compiles its own search; these shape the result.")

(defn with-columns
  "`stmt` with each column's `:query` applied: the visible columns, plus the
  sort column when it is hidden. A `:query` must not change how many rows
  match, because the count statement never gets it."
  [stmt columns sort]
  (let [sort-column (:column sort)
        columns (cond-> (vec columns)
                  (and sort-column
                       (not-any? #(= (:key %) (:key sort-column)) columns))
                  (conj sort-column))]
    (reduce (fn [s {:keys [query]}] (if query (query s) s))
            stmt
            columns)))

(defn add-select
  "Appends `exprs` to the statement's select. A search filter that joins turns
  `:select` into `:select-distinct`, and a statement can't carry both."
  [stmt & exprs]
  (let [k (if (contains? stmt :select-distinct) :select-distinct :select)]
    (update stmt k (fnil into []) exprs)))

(defn add-left-join
  "Left-joins `[table alias]` on `condition` unless a search filter has
  already joined `alias`."
  [stmt [table alias] condition]
  (let [joined? (->> (select-keys stmt [:join :left-join :join-by])
                     vals
                     (tree-seq coll? seq)
                     (some #(and (vector? %) (= 2 (count %)) (= alias (second %)))))]
    (if joined?
      stmt
      (update stmt :left-join (fnil into []) [[table alias] condition]))))

(defn order-by
  "The ORDER BY terms. A sort orders by its column's `:sort` expressions with
  nulls last, then `tiebreak`. With no sort, `relevance` then `default`."
  [sort {:keys [relevance default tiebreak]}]
  (if-let [{:keys [column dir]} sort]
    (let [direction (if (= :desc dir) :desc-nulls-last :asc-nulls-last)]
      (conj (mapv #(vector % direction) (:sort column)) tiebreak))
    (vec (concat relevance default))))
