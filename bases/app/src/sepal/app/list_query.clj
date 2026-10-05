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

(defn order-by
  "The ORDER BY terms. A sort orders by its column's `:sort` expressions with
  nulls last, then `tiebreak`. With no sort, `relevance` then `default`."
  [sort {:keys [relevance default tiebreak]}]
  (if-let [{:keys [column dir]} sort]
    (let [direction (if (= :desc dir) :desc-nulls-last :asc-nulls-last)]
      (conj (mapv #(vector % direction) (:sort column)) tiebreak))
    (vec (concat relevance default))))
