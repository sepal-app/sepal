(ns sepal.app.routes.propagation.export
  "CSV export handler for propagations."
  (:require [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.app.routes.propagation.index :as index]
            [sepal.database.interface :as db.i]
            [sepal.propagation.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(def ^:private columns
  "The list's columns plus both counts and the rootstock, which the table shows
  only on the record."
  [{:key :propagation/id :header "propagation_id" :column :p.id}
   {:key :propagation/type :header "propagation_type" :column :p.type}
   {:key :propagation/status :header "propagation_status" :column :p.status}
   {:key :propagation/propagated-on :header "propagated_on" :column :p.propagated_on}
   {:key :propagation/succeeded-on :header "succeeded_on" :column :p.succeeded_on}
   {:key :propagation/quantity-started :header "quantity_started" :column :p.quantity_started}
   {:key :propagation/quantity-succeeded :header "quantity_succeeded" :column :p.quantity_succeeded}
   {:key :accession/code :header "parent_accession_code" :column :a.code}
   {:key :material/code :header "parent_material_code" :column :m.code}
   {:key :location/code :header "location_code" :column :l.code}
   {:key :taxon/name :header "rootstock" :column :rt.name}])

;; Nothing optional to switch off: the export carries every propagation column
;; the record has.
(def export-options [])

(def ^:private Params
  [:map
   [:q {:default ""} :string]])

(defn handler
  "Export propagations as CSV, over the same rows the list shows."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        {:keys [q]} (params/decode Params query-params)
        ast (index/default-status-ast (search.i/parse q))

        base-stmt {:select (mapv :column columns)
                   :from [[:propagation :p]]
                   :join [[:accession :a] [:= :a.id :p.parent_accession_id]]
                   :left-join [[:material :m] [:= :m.id :p.parent_material_id]
                               [:location :l] [:= :l.id :p.location_id]
                               [:taxon :rt] [:= :rt.id :p.rootstock_taxon_id]]}

        stmt (-> (search.i/compile-query :propagation ast base-stmt)
                 (assoc :order-by [[:p.propagated_on :desc] [:p.id :desc]]))
        rows (db.i/execute! db stmt)

        csv-content (csv/rows->csv columns rows)
        filename (format "propagations-%s.csv"
                         (.format (LocalDateTime/now)
                                  (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")))]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
