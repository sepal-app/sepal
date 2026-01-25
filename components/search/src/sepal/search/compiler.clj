(ns sepal.search.compiler
  "Compile search AST to HoneySQL queries.

   The compiler uses field definitions from search-config to:
   - Map field names to SQL columns
   - Add necessary joins for related fields
   - Generate appropriate WHERE clauses based on field type"
  (:require [clojure.string :as str]))

(defn- column->id-column
  "Derive the ID column from a column reference.
   E.g., :t.name -> :t.id"
  [column]
  (let [col-str (name column)
        dot-idx (str/index-of col-str ".")]
    (if dot-idx
      (keyword (str (subs col-str 0 dot-idx) ".id"))
      ;; No table alias, assume :id
      :id)))

(defn- op->sql-op
  "Convert DSL operator string to HoneySQL operator keyword."
  [op]
  (case op
    ">"  :>
    "<"  :<
    ">=" :>=
    "<=" :<=
    :=))

(defn- field->clause
  "Convert a single filter to a HoneySQL WHERE clause.

   Arguments:
     filter    - Map with :field, :value/:values, :op, :negated
     field-def - Field definition from search-config with :column, :type, etc."
  [{:keys [value values op negated]} {:keys [column type fts-table]}]
  (let [clause (cond
                 ;; Multi-value → IN clause (no operator support)
                 ;; Enum values stored as strings in SQLite
                 values
                 [:in column values]

                 ;; Date comparison with operator
                 (and (= type :date) op)
                 [(op->sql-op op) column value]

                 ;; Date without operator → exact match
                 (= type :date)
                 [:= column value]

                 ;; Number comparison with operator
                 (and (= type :number) op)
                 [(op->sql-op op) column (parse-long value)]

                 ;; Number without operator → exact match
                 (= type :number)
                 [:= column (parse-long value)]

                 ;; FTS search - use subquery to correlate with joined table
                 ;; Generates: id_column IN (SELECT rowid FROM fts_table WHERE fts_table MATCH 'value*')
                 ;; The ID column is derived from the text column (e.g., :t.name -> :t.id)
                 (= type :fts)
                 (let [id-column (column->id-column column)]
                   [:in id-column {:select [:rowid]
                                   :from [fts-table]
                                   :where [:match fts-table (str value "*")]}])

                 ;; ID exact match
                 (= type :id)
                 [:= column (parse-long value)]

                 ;; Enum exact match (stored as strings in SQLite)
                 (= type :enum)
                 [:= column value]

                 ;; Count field - generates EXISTS or COUNT subquery based on operator
                 ;; Optimizes >0 to EXISTS and =0 to NOT EXISTS for performance
                 (= type :count)
                 (let [subquery-table fts-table  ; reusing fts-table key for the subquery table
                       join-condition column]    ; column holds the join condition
                   (cond
                     ;; >0 optimization: use EXISTS (faster than COUNT)
                     (and (= op ">") (= value "0"))
                     [:exists {:select [1]
                               :from [subquery-table]
                               :where join-condition
                               :limit 1}]

                     ;; =0 optimization: use NOT EXISTS
                     (and (or (nil? op) (= op "=")) (= value "0"))
                     [:not [:exists {:select [1]
                                     :from [subquery-table]
                                     :where join-condition
                                     :limit 1}]]

                     ;; >=1 is same as >0, use EXISTS
                     (and (= op ">=") (= value "1"))
                     [:exists {:select [1]
                               :from [subquery-table]
                               :where join-condition
                               :limit 1}]

                     ;; All other cases: use COUNT subquery
                     :else
                     [(op->sql-op (or op "="))
                      {:select [[[:count :*]]]
                       :from [subquery-table]
                       :where join-condition}
                      (parse-long value)]))

                 ;; Boolean flag (no value means true)
                 (nil? value)
                 [:= column true]

                 ;; Text with = operator → exact match
                 (= op "=")
                 [:= column value]

                 ;; Text contains (default)
                 :else
                 [:like column (str "%" value "%")])]
    (if negated
      [:not clause]
      clause)))

(defn- collect-joins
  "Gather unique joins from all filters, preserving order.

   Joins are deduplicated by table alias to avoid duplicate joins
   when multiple filters use the same related table, or when a join
   already exists in the base statement (either :join or :left-join)."
  [filters fields base-stmt]
  (let [;; Extract existing table aliases from base statement joins
        existing-aliases (->> (concat (:join base-stmt) (:left-join base-stmt))
                              (partition-all 2)
                              (map first)  ; get [table alias] pairs
                              set)]
    (->> filters
         (mapcat (fn [{:keys [field]}]
                   (get-in fields [(keyword field) :joins])))
         (partition-all 2)
         (reduce (fn [seen [tbl _condition :as join]]
                   (if (or (nil? tbl)
                           ;; Check if already in base statement
                           (contains? existing-aliases tbl)
                           ;; Check if we've already seen this table alias
                           ;; tbl is like [:taxon :t], we compare the whole pair
                           (some #(= (first %) tbl) seen))
                     seen
                     (conj seen join)))
                 [])
         (apply concat)
         vec)))

(defn- terms->clause
  "Convert free-text terms to an FTS clause.

   Finds the primary FTS-type field (one without joins) and uses its table
   for the match. Falls back to any FTS field if none are joinless.
   Terms are joined with spaces and suffixed with * for prefix matching.
   Uses a subquery to properly correlate with joined tables."
  [terms fields]
  (when (seq terms)
    (let [fts-fields (filter (fn [[_ v]] (= :fts (:type v))) fields)
          ;; Prefer FTS fields without joins (primary fields for this resource)
          primary-fts (or (first (filter (fn [[_ v]] (nil? (:joins v))) fts-fields))
                          (first fts-fields))]
      (when-let [[_ {:keys [column fts-table]}] primary-fts]
        (let [id-column (column->id-column column)]
          [:in id-column {:select [:rowid]
                          :from [fts-table]
                          :where [:match fts-table (str (str/join " " terms) "*")]}])))))

(defn compile-query
  "Compile a parsed AST into HoneySQL for a specific resource context.

   Arguments:
     fields    - Map of field definitions from search-config
     ast       - Parsed search AST with :terms and :filters
     base-stmt - Base HoneySQL statement (typically {:select [...] :from [...]})

   Returns: HoneySQL map with :where and :join clauses added

   When joins are present, uses :select-distinct to avoid duplicate rows.

   Example:
     (compile-query
       {:code {:column :m.code :type :text}
        :taxon {:column :t.name :type :fts :fts-table :taxon_fts
                :joins [[:accession :a] [:= :a.id :m.accession_id]
                        [:taxon :t] [:= :t.id :a.taxon_id]]}}
       {:filters [{:field \"taxon\" :value \"Quercus\"}]}
       {:select [:*] :from [[:material :m]]})
     ;; => {:select-distinct [:*]
     ;;     :from [[:material :m]]
     ;;     :join [[:accession :a] [:= :a.id :m.accession_id]
     ;;            [:taxon :t] [:= :t.id :a.taxon_id]]
     ;;     :where [:match :taxon_fts \"Quercus*\"]}"
  [fields {:keys [terms filters]} base-stmt]
  (let [;; Build WHERE clauses from filters
        filter-clauses (for [f filters
                             :let [field-def (get fields (keyword (:field f)))]
                             :when field-def]
                         (field->clause f field-def))

        ;; Build FTS clause from terms
        term-clause (terms->clause terms fields)

        ;; Combine all clauses
        all-clauses (cond-> (vec filter-clauses)
                      term-clause (conj term-clause))

        ;; Collect joins from all filters (excluding those already in base-stmt)
        joins (collect-joins filters fields base-stmt)

        ;; Build final WHERE clause
        where-clause (when (seq all-clauses)
                       (if (= 1 (count all-clauses))
                         (first all-clauses)
                         (into [:and] all-clauses)))

        ;; Merge new joins with existing joins from base-stmt
        existing-joins (or (:join base-stmt) [])
        all-joins (into existing-joins joins)
        has-filter-joins? (seq joins)]

    (cond-> base-stmt
        ;; Add/replace joins if we have any new ones
      has-filter-joins?
      (assoc :join all-joins)

        ;; Add WHERE clause
      where-clause
      (assoc :where where-clause)

        ;; Use DISTINCT when filter joins are added to avoid duplicates
        ;; (base joins are typically 1:1 for display, filter joins may be 1:many)
      has-filter-joins?
      (-> (dissoc :select)
          (assoc :select-distinct (or (:select base-stmt) [:*]))))))
