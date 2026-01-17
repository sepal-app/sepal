(ns sepal.app.routes.accession.export
  "CSV export handler for accessions.
   
   Uses GET with query params - conceptually correct since export is a read operation.
   The export modal uses Alpine-synced hidden inputs to ensure boolean params are
   always submitted (HTML checkboxes alone only submit when checked)."
  (:require [sepal.accession.interface.search]
            [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; =============================================================================
;; Column Definitions
;; =============================================================================

;; Column definitions use two keys:
;; - :key - The key to look up in DB results (namespaced, kebab-case to match DB output)
;; - :header - The CSV column header (snake_case as per plan)
;; - :column - The HoneySQL column expression

(def ^:private base-columns
  "Core accession columns. Order determines CSV column order."
  [{:key :accession/id :header "accession_id" :column :a.id}
   {:key :accession/code :header "accession_code" :column :a.code}
   {:key :accession/private :header "accession_private" :column :a.private}
   {:key :accession/id-qualifier :header "accession_id_qualifier" :column :a.id_qualifier}
   {:key :accession/id-qualifier-rank :header "accession_id_qualifier_rank" :column :a.id_qualifier_rank}
   {:key :accession/provenance-type :header "accession_provenance_type" :column :a.provenance_type}
   {:key :accession/wild-provenance-status :header "accession_wild_provenance_status" :column :a.wild_provenance_status}
   {:key :accession/date-received :header "accession_date_received" :column :a.date_received}
   {:key :accession/date-accessioned :header "accession_date_accessioned" :column :a.date_accessioned}])

(def ^:private taxon-columns
  "Optional taxon columns."
  [{:key :taxon/id :header "taxon_id" :column :t.id}
   {:key :taxon/name :header "taxon_name" :column :t.name}
   {:key :taxon/author :header "taxon_author" :column :t.author}
   {:key :taxon/rank :header "taxon_rank" :column :t.rank}])

(def ^:private collection-columns
  "Optional collection data columns.
   Note: SpatiaLite functions need aliases since they return keys based on function name."
  [{:key :collection/collected-date :header "collection_collected_date" :column :c.collected_date}
   {:key :collection/collector :header "collection_collector" :column :c.collector}
   {:key :collection/habitat :header "collection_habitat" :column :c.habitat}
   {:key :collection/country :header "collection_country" :column :c.country}
   {:key :collection/province :header "collection_province" :column :c.province}
   {:key :collection/locality :header "collection_locality" :column :c.locality}
   {:key :collection-latitude :header "collection_latitude" :column [[[:ST_Y :c.geo_coordinates]] :collection_latitude]}
   {:key :collection-longitude :header "collection_longitude" :column [[[:ST_X :c.geo_coordinates]] :collection_longitude]}
   {:key :collection/elevation :header "collection_elevation" :column :c.elevation}])

;; =============================================================================
;; Export Options (for modal UI)
;; =============================================================================

(def export-options
  "Options shown in export modal. IDs must match Params keys."
  [{:id "include_taxon"
    :label "Include taxon (name, author, rank)"}
   {:id "include_collection"
    :label "Include collection data (collector, locality, coordinates)"}])

;; =============================================================================
;; Handler
;; =============================================================================

(defn- parse-bool
  "Parse boolean string from form. Defaults to true if missing."
  [s]
  (if (nil? s)
    true
    (= s "true")))

(def ^:private Params
  [:map
   [:q {:default ""} :string]
   [:include_taxon {:default "true" :decode/form identity} :string]
   [:include_collection {:default "true" :decode/form identity} :string]])

(defn handler
  "Export accessions as CSV."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        decoded (params/decode Params query-params)
        q (:q decoded)
        include-taxon? (parse-bool (:include_taxon decoded))
        include-collection? (parse-bool (:include_collection decoded))
        
        ;; Parse search query
        ast (search.i/parse q)

        ;; Select columns based on options
        cols (cond-> base-columns
               include-taxon? (into taxon-columns)
               include-collection? (into collection-columns))

        ;; Build query with explicit column selection
        ;; Don't use aliases - let DB return natural keys (table/column in kebab-case)
        base-stmt (cond-> {:select (mapv :column cols)
                           :from [[:accession :a]]}
                    include-taxon?
                    (assoc :join [[:taxon :t] [:= :t.id :a.taxon_id]])
                    
                    include-collection?
                    (assoc :left-join [[:collection :c] [:= :c.accession_id :a.id]]))

        ;; Compile search query and execute
        stmt (-> (search.i/compile-query :accession ast base-stmt)
                 (assoc :order-by [:a.code]))
        rows (db.i/execute! db stmt)

        ;; Generate CSV
        csv-content (csv/rows->csv cols rows)
        
        ;; Filename includes date and time (colons replaced for filesystem safety)
        filename (format "accessions-%s.csv"
                         (.format (LocalDateTime/now)
                                  (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")))]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
