(ns sepal.search.parser
  "Parse search DSL strings into AST using instaparse.

   Syntax:
     - Bare words: full-text search terms
     - field:value: field filter
     - field:val1,val2: multi-value OR filter
     - field:>value, field:<value, field:>=value, field:<=value: comparison
     - \"quoted phrase\": exact phrase (term or value)
     - -term: exclude a free-text term
     - -field:value: negated filter

   A leading `-` on a bare word excludes that term from the full-text search,
   the way it does in Lucene, GitHub and Gmail. A bare word never names a
   field: naming one is what the colon is for, so a boolean field is asked for
   by value, `private:true` or `private:false`.

   Examples:
     quercus                    → free text term
     taxon:Quercus              → field filter
     taxon:\"Quercus alba\"     → quoted filter value
     location:GH,SH             → multi-value (OR)
     -quercus                   → excluded free text term
     -type:dead                 → negated filter
     private:true               → boolean filter
     \"red oak\"                → quoted phrase term
     created:>2024-01-01        → date after
     created:>=2024-01-01       → date on or after
     updated:<2024-06-01        → date before"
  (:require [clojure.string :as str]
            [instaparse.core :as insta]))

(def grammar
  "query     = term*
   term      = neg? (filter / phrase / word)
   neg       = <'-'>
   filter    = field <':'> op? values
   field     = #'[a-z][a-z0-9]*' (<'.'> #'[a-z][a-z0-9]*')*
   op        = '>=' / '<=' / '>' / '<' / '='
   values    = value (<','> value)*
   value     = quoted / unquoted
   quoted    = <'\"'> #'[^\"]*' <'\"'>
   unquoted  = #'[^,\"\\s]+'
   phrase    = <'\"'> #'[^\"]+' <'\"'>
   word      = #'[^\"\\s:]+'")

(def parser
  (insta/parser grammar :auto-whitespace :standard))

(def ^:private empty-ast
  {:terms [] :filters [] :excluded-terms []})

(defn- transform-ast
  "Transform instaparse tree to our AST format."
  [tree]
  (insta/transform
    {:query   (fn [& terms]
                (reduce
                  (fn [ast {:keys [type] :as item}]
                    (case type
                      :term (update ast :terms conj (:value item))
                      :excluded-term (update ast :excluded-terms conj (:value item))
                      :filter (update ast :filters conj (dissoc item :type))
                      ;; Handle nil items (shouldn't happen but be safe)
                      ast))
                  empty-ast
                  terms))

     :term    (fn [& parts]
                (let [negated? (= (first parts) :negated)
                      content (if negated? (second parts) (first parts))]
                  (cond
                    ;; Negated bare word or phrase - a term to exclude.
                    ;; Must check this before map? since :word returns a map
                    (and negated? (= :term (:type content)))
                    {:type :excluded-term :value (:value content)}

                    ;; Filter or phrase - just attach negation
                    (map? content)
                    (assoc content :negated negated?)

                    ;; Regular term (shouldn't happen given :word returns map)
                    :else
                    content)))

     :neg     (constantly :negated)

     :filter  (fn [& args]
                ;; args can be: [field values] or [field op values]
                (let [field (first args)
                      op (when (string? (second args)) (second args))
                      values (if op (nth args 2) (second args))]
                  {:type :filter
                   :field field
                   :op op
                   :value (when (= 1 (count values)) (first values))
                   :values (when (> (count values) 1) values)
                   :negated false}))

     :field   (fn [& parts] (str/join "." parts))
     :op      identity
     :values  (fn [& vs] (vec vs))
     :value   identity
     :quoted  identity
     :unquoted identity
     :phrase  (fn [text] {:type :term :value text})
     :word    (fn [text] {:type :term :value text})}
    tree))

(defn- clean-ast
  "Remove nil values from filter maps."
  [ast]
  (update ast :filters
          (fn [filters]
            (mapv (fn [f]
                    (into {} (remove (fn [[_ v]] (nil? v)) f)))
                  filters))))

(defn parse
  "Parse a search query string into an AST.

   Returns a map with:
     :terms          - vector of free-text search terms
     :filters        - vector of filter maps with :field, :value/:values, :op,
                       :negated
     :excluded-terms - vector of free-text terms to exclude

   On parse failure, includes :error key with failure info.

   Examples:
     (parse \"quercus\")
     ;; => {:terms [\"quercus\"] :filters [] :excluded-terms []}

     (parse \"taxon:Quercus location:GH,SH\")
     ;; => {:terms []
     ;;     :filters [{:field \"taxon\" :value \"Quercus\" :negated false}
     ;;               {:field \"location\" :values [\"GH\" \"SH\"] :negated false}]
     ;;     :excluded-terms []}

     (parse \"-quercus \\\"red oak\\\"\")
     ;; => {:terms [\"red oak\"]
     ;;     :filters []
     ;;     :excluded-terms [\"quercus\"]}

     (parse \"created:>2024-01-01\")
     ;; => {:terms []
     ;;     :filters [{:field \"created\" :op \">\" :value \"2024-01-01\" :negated false}]
     ;;     :excluded-terms []}"
  [query-string]
  (if (str/blank? query-string)
    empty-ast
    (let [result (parser query-string)]
      (if (insta/failure? result)
        (assoc empty-ast :error (insta/get-failure result))
        (-> result transform-ast clean-ast)))))

(defn parse-error?
  "Check if parse result contains an error."
  [ast]
  (contains? ast :error))

(defn failure-message
  "Get human-readable error message from failed parse.
   Returns nil if parse was successful."
  [ast]
  (when-let [failure (:error ast)]
    (with-out-str (println failure))))

(defn unparse
  "Convert an AST back to a query string.

   Useful for generating clear-href URLs when removing filters."
  [{:keys [terms filters excluded-terms]}]
  (let [quote-if-needed (fn [s]
                          (if (or (str/includes? s " ")
                                  (str/includes? s ",")
                                  (str/includes? s "\""))
                            (str "\"" (str/replace s "\"" "\\\"") "\"")
                            s))
        filter-strs (for [{:keys [field op value values negated]} filters
                          :when (or value values negated)]
                      (str (when negated "-")
                           field
                           (when (or value values) ":")
                           (or op "")
                           (cond
                             values (str/join "," (map quote-if-needed values))
                             value (quote-if-needed value))))
        excluded-strs (map #(str "-" (quote-if-needed %)) excluded-terms)
        term-strs (map quote-if-needed terms)]
    (str/join " " (concat filter-strs excluded-strs term-strs))))
