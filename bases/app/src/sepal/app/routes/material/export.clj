(ns sepal.app.routes.material.export
  "CSV export handler for materials."
  (:require [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.database.interface :as db.i]
            [sepal.material.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

;; =============================================================================
;; Column Definitions
;; =============================================================================

(def ^:private base-columns
  "Core material columns + required location."
  [{:key :material/id :header "material_id" :column :m.id}
   {:key :material/code :header "material_code" :column :m.code}
   {:key :material/type :header "material_type" :column :m.type}
   {:key :material/status :header "material_status" :column :m.status}
   {:key :material/quantity :header "material_quantity" :column :m.quantity}
   {:key :material/memorial :header "material_memorial" :column :m.memorial}
   ;; Location is required FK, always included
   {:key :location/id :header "location_id" :column :l.id}
   {:key :location/code :header "location_code" :column :l.code}
   {:key :location/name :header "location_name" :column :l.name}])

(def ^:private taxon-columns
  "Optional taxon columns (via accession -> taxon)."
  [{:key :taxon/id :header "taxon_id" :column :t.id}
   {:key :taxon/name :header "taxon_name" :column :t.name}
   {:key :taxon/author :header "taxon_author" :column :t.author}
   {:key :taxon/rank :header "taxon_rank" :column :t.rank}])

(def ^:private accession-columns
  "Optional accession columns."
  [{:key :accession/id :header "accession_id" :column :a.id}
   {:key :accession/code :header "accession_code" :column :a.code}
   {:key :accession/provenance-type :header "accession_provenance_type" :column :a.provenance_type}
   {:key :accession/wild-provenance-status :header "accession_wild_provenance_status" :column :a.wild_provenance_status}
   {:key :accession/date-received :header "accession_date_received" :column :a.date_received}
   {:key :accession/date-accessioned :header "accession_date_accessioned" :column :a.date_accessioned}])

;; =============================================================================
;; Export Options
;; =============================================================================

(def export-options
  "Options shown in export modal."
  [{:id "include_taxon"
    :label "Include taxon (name, author, rank)"}
   {:id "include_accession"
    :label "Include accession data (code, provenance, dates)"}])

;; =============================================================================
;; Handler
;; =============================================================================

(defn- parse-bool [s]
  (if (nil? s) true (= s "true")))

(def ^:private Params
  [:map
   [:q {:default ""} :string]
   [:include_taxon {:default "true" :decode/form identity} :string]
   [:include_accession {:default "true" :decode/form identity} :string]])

(defn handler
  "Export materials as CSV."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        decoded (params/decode Params query-params)
        q (:q decoded)
        include-taxon? (parse-bool (:include_taxon decoded))
        include-accession? (parse-bool (:include_accession decoded))

        ast (search.i/parse q)

        ;; Build columns based on options
        cols (cond-> base-columns
               include-taxon? (into taxon-columns)
               include-accession? (into accession-columns))

        ;; Build query - taxon requires joining through accession
        ;; Even if accession columns aren't selected, we need the join for taxon
        needs-accession-join? (or include-taxon? include-accession?)

        base-stmt (cond-> {:select (mapv :column cols)
                           :from [[:material :m]]
                           ;; Location is always joined (required FK)
                           :join [[:location :l] [:= :l.id :m.location_id]]}
                    ;; Join accession if needed for taxon or accession data
                    needs-accession-join?
                    (update :join into [[:accession :a] [:= :a.id :m.accession_id]])

                    ;; Join taxon through accession
                    include-taxon?
                    (update :join into [[:taxon :t] [:= :t.id :a.taxon_id]]))

        stmt (-> (search.i/compile-query :material ast base-stmt)
                 (assoc :order-by [:m.code]))
        rows (db.i/execute! db stmt)

        csv-content (csv/rows->csv cols rows)
        filename (format "materials-%s.csv"
                         (.format (LocalDateTime/now)
                                  (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")))]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
