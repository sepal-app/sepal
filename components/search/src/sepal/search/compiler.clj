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

(defn- terms->match
  "Free-text terms as an FTS5 MATCH expression, or nil when none are left.

   Every term becomes one quoted FTS5 phrase and the last is prefix-extended,
   so `quercus alb` compiles to `\"quercus\" \"alb\"*` and the quoted phrase
   `\"red oak\"` compiles to `\"red oak\"*`.

   A term is a phrase when it holds a space, which only a quoted one can:
   `word` in the grammar is `#'[^\"\\s:]+'`. The term arrives whole and has to
   stay whole. Split into `\"red\" \"oak\"*` it was byte-identical to the
   unquoted `red oak`, and FTS5 reads that as AND -- `oak red` and `red maple
   and oak tree` came back from a query asking for a phrase.

   The prefix rule is unchanged: the final token of the whole expression gets
   the `*`, and FTS5 applies a `*` written after a phrase to that phrase's last
   token. So a trailing `\"red oa\"` still finds `red oak`, which is what the
   query builder needs -- it quotes any filter value the user types a space
   into, so the quotes there are not the user asking for exactness. A phrase
   with a term after it is exact, since the `*` belongs to the term.

   The quoting is the load-bearing part, not decoration. FTS5 gives `.`, `:`,
   `-`, `'`, `(`, `)` and the barewords AND/OR/NOT meanings of their own inside
   a MATCH expression, so any raw term is parsed as syntax and a 500 is one
   keystroke away. Measured against taxon_fts on 2026-09-05: `sp.` is
   `syntax error near \".\"`, `sub-alpine` is `no such column: alpine`, `AND` is
   `syntax error near \"AND\"`, and a lone `\"` is `unterminated string` -- all
   ordinary things to type, and `sp.` especially so. Inside double quotes the
   tokenizer splits the token instead, so nothing a user can type is syntax.
   An embedded double quote is escaped FTS5's way, by doubling it.

   Quoting leaves normal queries alone: `Enc`, `Rosa`, `Quercus alba` and `x`
   all return identical row counts either way. It does change one thing --
   `a OR b` used to reach FTS5's OR operator and now searches for the literal
   word `OR`. That was never a feature: the parser documents bare words as
   search terms, and `a AND b` errored while `a OR b` worked, so there was no
   coherent behaviour to preserve.

   `components/synonym/src/sepal/synonym/reference.clj` carries the same logic
   for the same reason, against a different database on a different pool."
  [terms]
  (let [phrases (->> terms
                     (map #(->> (str/split (str/trim (or % "")) #"\s+")
                                (remove str/blank?)
                                (str/join " ")))
                     (remove str/blank?))]
    (when (seq phrases)
      (let [quoted (mapv #(str "\"" (str/replace % "\"" "\"\"") "\"") phrases)]
        (str/join " " (conj (vec (butlast quoted)) (str (last quoted) "*")))))))

(defn- field->clause
  "Convert a single filter to a HoneySQL WHERE clause.

   Arguments:
     filter    - Map with :field, :value/:values, :op, :negated
     field-def - Field definition from search-config with :column, :type, etc."
  [{:keys [value values op negated] :as parsed} {:keys [column type fts-table id-column filter-clause]}]
  (let [clause (cond
                 ;; The field says what its filter means, for one no single
                 ;; column comparison can express.
                 filter-clause
                 (filter-clause parsed)

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
                 ;; Quoted, for the same reason terms->match quotes: a filter
                 ;; value reaches MATCH just as directly as a bare term does,
                 ;; so `taxon:sp.` is a 500 without this.
                 ;; `=` on a full-text field asks for the column, not the
                 ;; index. FTS5 splits on `.`, so an accession code like
                 ;; `2022.0001` indexes as the tokens `2022` and `0001` and no
                 ;; MATCH can name that one row. Handled here rather than by
                 ;; moving the `=` branch above this one, which would also take
                 ;; `:number`, `:id` and `:count` away from their own branches
                 ;; and drop their parse-long and EXISTS handling.
                 (and (= type :fts) (= op "="))
                 [:= column value]

                 (= type :fts)
                 (when-let [match (terms->match [value])]
                   [:in (or id-column (column->id-column column))
                    {:select [:rowid]
                     :from [fts-table]
                     :where [:match fts-table match]}])

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

                 ;; Boolean by value: private:true / private:false. The value
                 ;; is the string the user typed, and anything that is neither
                 ;; is compared to the column as it arrived: the column holds 1
                 ;; or 0, so private:yes matches no row. That keeps a filter
                 ;; narrowing. Dropping it instead would answer a question the
                 ;; compiler could not read by returning every row, which is
                 ;; the one failure mode a filter must not have.
                 (= type :boolean)
                 [:= column (if (nil? value)
                              true
                              (if-some [b (parse-boolean (str/lower-case value))]
                                b
                                value))]

                 ;; Flag with no value means true
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
  "Gather unique joins from the given field definitions, preserving order.

   Joins are deduplicated by table alias to avoid duplicate joins
   when multiple filters use the same related table, or when a join
   already exists in the base statement (either :join or :left-join)."
  [field-defs base-stmt]
  (let [;; Extract existing table aliases from base statement joins
        existing-aliases (->> (concat (:join base-stmt) (:left-join base-stmt))
                              (partition-all 2)
                              (map first)  ; get [table alias] pairs
                              set)]
    (->> field-defs
         (mapcat :joins)
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

(defn- primary-fts-field
  "The FTS field a free-text query searches: the resource's own, falling back
  to the first of any.

  Its own means reached without a join and without an :id-column. Both of those
  say the field indexes some other row -- :parent on :taxon is the taxon table's
  own FTS table keyed by the child's parent_id -- and a bare word typed into the
  search box is about the resource, not its relations. Without the :id-column
  half, dropping :joins from such a field silently made it the field every
  free-text search ran against, and every search returned nothing."
  [fields]
  (let [fts-fields (filter (fn [[_ v]] (= :fts (:type v))) fields)]
    (or (first (filter (fn [[_ v]] (and (nil? (:joins v))
                                        (nil? (:id-column v))))
                       fts-fields))
        (first fts-fields))))

(defn- fts-in-clause
  "`id-column IN (rowids matching)`. A subquery, so it correlates with a joined
   table rather than needing one."
  [{:keys [column fts-table id-column]} match]
  [:in (or id-column (column->id-column column))
   {:select [:rowid]
    :from [fts-table]
    :where [:match fts-table match]}])

(defn- searchable-fields
  "The fields a bare word searches, in the order they are declared.

   A resource marks them `:search? true`. With none marked the bare word goes
   to the one primary FTS field, which is what every resource but material
   wants: its own name or code."
  [fields]
  (let [marked (filter (fn [[_ v]] (:search? v)) fields)]
    (if (seq marked)
      marked
      (when-let [primary (primary-fts-field fields)]
        [primary]))))

(defn- terms->clause
  "Convert free-text terms to a clause over every field the resource says a
   bare word searches.

   ORed, because they are alternatives: a material is identified by its own
   code, by the code of the accession it came from and by the plant it is, and
   which of those someone types is not something the search gets to choose."
  [terms fields opts]
  (when-let [match (terms->match terms)]
    (let [clauses (->> (searchable-fields fields)
                       (keep (fn [[_ {:keys [type search-clause] :as field}]]
                               (cond
                                 ;; The field says how a bare word matches it,
                                 ;; for a match one column can't express.
                                 search-clause
                                 (when-let [value (first terms)]
                                   (search-clause value opts))

                                 (= :fts type)
                                 (fts-in-clause field match)

                                 ;; A plain column has no index behind it, so
                                 ;; this is the ordinary contains match the
                                 ;; same field gives as a filter.
                                 :else
                                 (when-let [value (first terms)]
                                   [:like (:column field) (str "%" value "%")]))))
                       (vec))]
      (case (count clauses)
        0 nil
        1 (first clauses)
        (into [:or] clauses)))))

(defn relevance-order
  "Order-by terms putting the closest names first, or nil when the query has no
  free-text part.

  Four bands over the field the free-text search reads: the query as the whole
  value, as the start of it, as the start of a word in it, and then everything
  else the FTS match turned up. `Aa` is a genus, so two characters has to keep
  working and an exact match has to win outright.

  All four are string tests against a value already on the row, so they cost
  the sort that was happening anyway. The bm25 ranking this replaces had to
  score every matched row against every term a prefix expands to, which is
  cheap for `prunus` and 10s for `pr`.

  Returns terms to put before a caller's own ordering, not to replace it. Ties
  inside a band are the common case, and an unbroken tie orders arbitrarily,
  which a page offset turns into rows repeated on one page and missing from the
  next."
  [fields {:keys [terms]}]
  (when (seq terms)
    (when-let [[_ {:keys [column]}] (primary-fts-field fields)]
      (let [query (str/lower-case (str/join " " terms))
            value [:lower column]]
        [[[:case
           [:= value query] 0
           [:= [:instr value query] 1] 1
           ;; A space in front of both, so the query has to start a word rather
           ;; than land mid-one: a curator typing an epithet wants Asimina
           ;; triloba above a name that merely contains the letters.
           [:> [:instr [:|| " " value] (str " " query)] 0] 2
           :else 3]
          :asc]]))))

(defn compile-query
  "Compile a parsed AST into HoneySQL for a specific resource context.

   Arguments:
     fields    - Map of field definitions from search-config
     ast       - Parsed search AST with :terms, :filters and :excluded-terms
     base-stmt - Base HoneySQL statement (typically {:select [...] :from [...]})

   Returns: HoneySQL map with :where and :join clauses added

   When joins are present, uses :select-distinct to avoid duplicate rows.

   `opts` is passed to every field's `:search-clause`, for a clause that needs
   a value only the caller has, such as a garden setting.

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
  [fields {:keys [terms filters excluded-terms]} base-stmt & [opts]]
  (let [;; Build WHERE clauses from filters
        filter-clauses (for [f filters
                             :let [field-def (get fields (keyword (:field f)))]
                             :when field-def]
                         (field->clause f field-def))

        ;; Build FTS clause from terms
        term-clause (terms->clause terms fields opts)

        ;; Each excluded term is the negation of the clause the same term
        ;; would have compiled to on its own, so `quercus` and `-quercus`
        ;; partition the rows between them. One MATCH of several excluded
        ;; terms would not: FTS5 reads `"quercus" "rosa"*` as AND, and negating
        ;; that keeps every row matching just one of them -- measured against
        ;; SQLite on 2026-09-11, where it returned all four rows of a four-row
        ;; table instead of the one row matching neither.
        ;;
        ;; [:not [:in ...]] and a bare NOT IN are the same expression in
        ;; SQLite, NULLs included, and this one matches how field->clause
        ;; already negates. A row the subquery does not return is kept, which
        ;; is what makes a row with no FTS entry at all survive an exclusion:
        ;; it matches no term, so no exclusion should remove it. The id column
        ;; is never NULL itself -- an FTS5 rowid cannot be, and every FTS field
        ;; is reached by an inner join.
        excluded-clauses (for [term excluded-terms
                               :let [clause (terms->clause [term] fields opts)]
                               :when clause]
                           [:not clause])

        ;; Combine all clauses
        all-clauses (-> (vec filter-clauses)
                        (cond-> term-clause (conj term-clause))
                        (into excluded-clauses))

        ;; Joins for every field the query reads, not only the filtered ones:
        ;; a bare word can search a related table too, and a caller whose base
        ;; statement happens not to join it would otherwise compile SQL naming
        ;; a column that is not there.
        joins (collect-joins (concat (keep #(get fields (keyword (:field %))) filters)
                                     ;; Keyed off the clause rather than the
                                     ;; terms: terms that are all blank compile
                                     ;; to nothing, and a join for a search that
                                     ;; is not happening is a join that changes
                                     ;; the row count for no reason.
                                     (when term-clause
                                       (map second (searchable-fields fields))))
                             base-stmt)

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
