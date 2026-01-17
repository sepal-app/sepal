(ns sepal.search.interface
  "Extensible search DSL for resource list pages.

   This component provides a unified search system where:
   - Users can type queries like `taxon:Quercus location:GH type:seed`
   - Each resource component registers its searchable fields
   - The system compiles queries to HoneySQL with appropriate joins

   ## Extension Point

   Resources register their search configuration via `defmethod search-config`:

     (defmethod search.i/search-config :taxon [_]
       {:table [:taxon :t]
        :fields
        {:name {:column :t.name :type :fts :fts-table :taxon_fts :label \"Name\"}
         :rank {:column :t.rank :type :enum :values [:species :genus] :label \"Rank\"}
         :location {:column :l.code :type :text :label \"Location\"
                    :joins [[:accession :a] [:= :a.taxon_id :t.id]
                            [:material :m] [:= :m.accession_id :a.id]
                            [:location :l] [:= :l.id :m.location_id]]}}})

   ## Field Types

   - :text     - Text contains search (LIKE %value%)
   - :fts      - Full-text search (requires :fts-table)
   - :enum     - Exact match with keyword coercion
   - :id       - Exact match with integer coercion
   - :boolean  - Boolean flag (presence means true)

   ## Usage

     (require '[sepal.search.interface :as search.i])

     ;; Parse user query
     (def ast (search.i/parse \"taxon:Quercus location:GH\"))

     ;; Compile to HoneySQL
     (def stmt (search.i/compile-query :material ast
                 {:select [:*] :from [[:material :m]]}))

     ;; Get filter badges for UI
     (def badges (search.i/ast->filter-badges :material ast))"
  (:require [clojure.string :as str]
            [sepal.search.compiler :as compiler]
            [sepal.search.parser :as parser]))

;; =============================================================================
;; Extension Point: Resources register their searchable fields
;; =============================================================================

(defmulti search-config
  "Returns search configuration for a resource type.

   Dispatch: resource type keyword (:taxon, :material, :accession, :location, :contact)

   Returns a map with:
     :table  - [table-name alias] e.g., [:material :m]
     :fields - map of field-key → field-definition

   Field definition keys:
     :column    - HoneySQL column reference (e.g., :t.name, :m.type)
     :type      - One of :text :fts :enum :boolean :id
     :label     - Human-readable label for UI
     :joins     - Vector of [table-alias join-condition] pairs (optional)
     :values    - For :enum type, the allowed values (optional)
     :fts-table - For :fts type, the FTS virtual table name"
  (fn [resource-type] resource-type))

(defmethod search-config :default [resource-type]
  (throw (ex-info (str "No search configuration defined for resource: " resource-type)
                  {:resource-type resource-type})))

;; =============================================================================
;; Configuration Accessors
;; =============================================================================

(defn get-table
  "Get the base table for a resource type.
   Returns [table-name alias] e.g., [:material :m]"
  [resource-type]
  (:table (search-config resource-type)))

(defn get-fields
  "Get the field definitions for a resource type.
   Returns map of field-key → field-definition."
  [resource-type]
  (:fields (search-config resource-type)))

(defn get-field
  "Get a specific field definition.
   Returns nil if field not found."
  [resource-type field-key]
  (get-in (search-config resource-type) [:fields (keyword field-key)]))

;; =============================================================================
;; Parsing
;; =============================================================================

(defn parse
  "Parse a search query string into an AST.

   Returns a map with:
     :terms   - vector of free-text search terms
     :filters - vector of filter maps with :field, :value/:values, :negated

   On parse failure, includes :error key with failure info.

   Syntax:
     - Bare words: full-text search terms
     - field:value: field filter
     - field:val1,val2: multi-value OR filter
     - \"quoted phrase\": exact phrase
     - -term or -field:value: negation

   Examples:
     (parse \"quercus\")
     ;; => {:terms [\"quercus\"] :filters []}

     (parse \"taxon:Quercus location:GH,SH -private\")
     ;; => {:terms []
     ;;     :filters [{:field \"taxon\" :value \"Quercus\" :negated false}
     ;;               {:field \"location\" :values [\"GH\" \"SH\"] :negated false}
     ;;               {:field \"private\" :negated true}]}"
  [query-string]
  (parser/parse query-string))

(defn unparse
  "Convert an AST back to a query string.

   Useful for generating clear-href URLs when removing filters.

   Example:
     (unparse {:terms [\"alba\"]
               :filters [{:field \"taxon\" :value \"Quercus\"}]})
     ;; => \"taxon:Quercus alba\""
  [ast]
  (parser/unparse ast))

(defn parse-error?
  "Check if parse result contains an error."
  [ast]
  (parser/parse-error? ast))

(defn failure-message
  "Get human-readable error message from failed parse.
   Returns nil if parse was successful."
  [ast]
  (parser/failure-message ast))

;; =============================================================================
;; Compilation
;; =============================================================================

(defn compile-query
  "Compile a parsed AST into HoneySQL for a specific resource context.

   Arguments:
     resource-type - The resource being queried (:taxon, :material, etc.)
     ast           - Parsed query AST from `parse`
     base-stmt     - Base HoneySQL statement {:select [...] :from [...]}

   Returns: HoneySQL map with :where and :join clauses added

   Example:
     (compile-query :material
                    {:filters [{:field \"taxon\" :value \"Quercus\"}
                               {:field \"type\" :values [\"seed\" \"plant\"]}]}
                    {:select [:*] :from [[:material :m]]})
     ;; => {:select-distinct [:*]
     ;;     :from [[:material :m]]
     ;;     :join [...]
     ;;     :where [:and ...]}"
  [resource-type ast base-stmt]
  (let [fields (get-fields resource-type)]
    (compiler/compile-query fields ast base-stmt)))

;; =============================================================================
;; UI Helpers
;; =============================================================================

(defn field-options
  "Get searchable fields formatted for UI dropdowns.

   Returns a sequence of maps sorted by label, with all values as strings
   for easy JSON serialization. Excludes :id and :boolean type fields
   which are for programmatic use, not user-facing search.

     [{:key \"code\" :label \"Code\" :type \"text\"}
      {:key \"type\" :label \"Type\" :type \"enum\" :values [\"seed\" \"plant\" ...]}
      ...]"
  [resource-type]
  (->> (get-fields resource-type)
       (remove (fn [[_ v]] (#{:id :boolean} (:type v))))
       (map (fn [[k v]]
              (cond-> {:key (name k)
                       :label (:label v)
                       :type (name (:type v))}
                (:values v) (assoc :values (mapv name (:values v))))))
       (sort-by :label)))

(defn ast->filter-badges
  "Convert parsed AST to filter badge data for UI display.

   Each badge includes:
     :label    - Human-readable field label
     :value    - Display value (joined with ', ' for multi-value)
     :negated  - Whether filter is negated
     :clear-q  - Query string with this filter removed

   Example:
     (ast->filter-badges :material
                         {:filters [{:field \"taxon\" :value \"Quercus\"}
                                    {:field \"location\" :value \"GH\"}]
                          :terms [\"alba\"]})
     ;; => [{:label \"Taxon\" :value \"Quercus\" :negated false
     ;;      :clear-q \"location:GH alba\"}
     ;;     {:label \"Location\" :value \"GH\" :negated false
     ;;      :clear-q \"taxon:Quercus alba\"}]"
  [resource-type {:keys [terms filters] :as ast}]
  (let [fields (get-fields resource-type)]
    (for [{:keys [field value values negated] :as f} filters]
      (let [field-def (get fields (keyword field))
            other-filters (remove #(= % f) filters)
            clear-q (unparse {:filters other-filters :terms terms})]
        {:label (or (:label field-def) (str/capitalize field))
         :value (or (some->> values (str/join ", ")) value "")
         :negated (boolean negated)
         :clear-q clear-q}))))

(defn validate-query
  "Validate a parsed AST against available fields for a resource.

   Returns {:valid true} if all filter fields are recognized,
   or {:valid false :errors [...]} with details about unknown fields.

   Example:
     (validate-query :material {:filters [{:field \"bogus\" :value \"x\"}]})
     ;; => {:valid false
     ;;     :errors [{:field \"bogus\" :message \"Unknown field\"}]}"
  [resource-type {:keys [filters]}]
  (let [valid-fields (set (keys (get-fields resource-type)))
        errors (for [{:keys [field]} filters
                     :when (not (contains? valid-fields (keyword field)))]
                 {:field field :message "Unknown field"})]
    (if (empty? errors)
      {:valid true}
      {:valid false :errors (vec errors)})))
