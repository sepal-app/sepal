(ns sepal.app.list-sort
  "A list's sort, as named by `sort` and `dir` in the URL. Params maps here
  have string keys, as Ring's query-params do."
  (:require [clojure.string :as str]
            [sepal.search.interface :as search.i]))

(defn sort-first
  "The direction of a first click on `column`'s header. Dates go newest first."
  [column]
  (or (:sort-first column)
      (if (#{:date :datetime} (:type column)) :desc :asc)))

(def ^:private directions {"asc" :asc "desc" :desc})

(defn resolve-sort
  "The sort `params` asks for, as {:column column :dir dir}, or nil for the
  list's default order. `columns` is every column the list has, hidden or
  not: a URL that sorts by a hidden column is honoured."
  [columns params]
  (when-let [k (get params "sort")]
    (when-let [column (some #(when (and (:sort %) (= k (name (:key %)))) %) columns)]
      (let [dir-param (get params "dir")
            dir (if dir-param (get directions dir-param) (sort-first column))]
        (when dir
          {:column column :dir dir})))))

(defn next-sort
  "Where a click on `column`'s header goes from `sort`: its first direction,
  then the other, then back to the default order."
  [column sort]
  (let [first-dir (sort-first column)]
    (cond
      (not= (:key column) (get-in sort [:column :key])) {:column column :dir first-dir}
      (= first-dir (:dir sort)) {:column column :dir (if (= :asc first-dir) :desc :asc)}
      :else nil)))

(defn sort-params
  "The URL parameters for `sort`, or nil for the default order."
  [sort]
  (when sort
    {:sort (name (get-in sort [:column :key]))
     :dir (name (:dir sort))}))

(defn- free-text
  "A query's free-text terms, lower-cased. Field filters don't count."
  [q]
  (-> (str/join " " (:terms (search.i/parse (or q ""))))
      str/trim
      str/lower-case))

(defn refined?
  "Whether going from `old` free text to `new` refines a search rather than
  starting, clearing or replacing one."
  [old new]
  (or (= old new)
      (and (seq old)
           (seq new)
           (or (str/starts-with? new old)
               (str/starts-with? old new)))))

(defn carry
  "`params` with `previous`'s sort added, when `params` names none and its
  free text only refines `previous`'s."
  [params previous]
  (if (and (not (contains? params "sort"))
           (contains? previous "sort")
           (refined? (free-text (get previous "q")) (free-text (get params "q"))))
    (merge params (select-keys previous ["sort" "dir"]))
    params))
