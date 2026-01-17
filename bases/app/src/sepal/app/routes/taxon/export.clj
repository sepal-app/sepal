(ns sepal.app.routes.taxon.export
  "CSV export handler for taxa."
  (:require [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface.search]
            [zodiac.core :as z])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; =============================================================================
;; Column Definitions
;; =============================================================================

(def ^:private base-columns
  "Core taxon columns."
  [{:key :taxon/id :header "taxon_id" :column :t.id}
   {:key :taxon/name :header "taxon_name" :column :t.name}
   {:key :taxon/author :header "taxon_author" :column :t.author}
   {:key :taxon/rank :header "taxon_rank" :column :t.rank}
   {:key :taxon/wfo-taxon-id :header "taxon_wfo_taxon_id" :column :t.wfo_taxon_id}
   {:key :taxon/parent-id :header "taxon_parent_id" :column :t.parent_id}])

(def ^:private parent-columns
  "Optional parent taxon columns (via LEFT JOIN self-reference).
   Use [column alias] format for simple column aliasing."
  [{:key :parent-name :header "parent_name" :column [:p.name :parent_name]}
   {:key :parent-author :header "parent_author" :column [:p.author :parent_author]}
   {:key :parent-rank :header "parent_rank" :column [:p.rank :parent_rank]}])

;; =============================================================================
;; Export Options
;; =============================================================================

(def export-options
  "Options shown in export modal."
  [{:id "include_parent"
    :label "Include parent taxon (name, author, rank)"}])

;; =============================================================================
;; Handler
;; =============================================================================

(defn- parse-bool [s]
  (if (nil? s) true (= s "true")))

(def ^:private Params
  [:map
   [:q {:default ""} :string]
   [:include_parent {:default "true" :decode/form identity} :string]])

(defn handler
  "Export taxa as CSV."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        decoded (params/decode Params query-params)
        q (:q decoded)
        include-parent? (parse-bool (:include_parent decoded))

        ast (search.i/parse q)

        cols (cond-> base-columns
               include-parent? (into parent-columns))

        base-stmt (cond-> {:select (mapv :column cols)
                           :from [[:taxon :t]]}
                    include-parent?
                    (assoc :left-join [[:taxon :p] [:= :p.id :t.parent_id]]))

        stmt (-> (search.i/compile-query :taxon ast base-stmt)
                 (assoc :order-by [:t.name]))
        rows (db.i/execute! db stmt)

        csv-content (csv/rows->csv cols rows)
        filename (format "taxa-%s.csv"
                         (.format (LocalDateTime/now)
                                  (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")))]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
