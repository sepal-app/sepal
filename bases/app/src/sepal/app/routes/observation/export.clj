(ns sepal.app.routes.observation.export
  "CSV export handler for observations."
  (:require [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.app.ui.location-path :as location-path]
            [sepal.database.interface :as db.i]
            [sepal.observation.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z]))

(def ^:private columns
  "Observation columns, plus the polymorphic subject resolved through the
  material and location tables -- exactly one pair is populated per row,
  depending on resource_type."
  [{:key :observation/id :header "observation_id" :column :o.id}
   {:key :observation/type :header "observation_type" :column :o.type}
   {:key :observation/value :header "observation_value" :column :o.value}
   {:key :observation/observed-on :header "observed_on" :column :o.observed_on}
   {:key :observation/observed-by :header "observed_by" :column :o.observed_by}
   {:key :observation/next-check-on :header "next_check_on" :column :o.next_check_on}
   {:key :observation/note :header "note" :column :o.note}
   {:key :observation/resource-type :header "subject_type" :column :o.resource_type}
   {:key :observation/resource-id :header "subject_id" :column :o.resource_id}
   {:key :accession/code :header "accession_code" :column :acc.code}
   {:key :material/code :header "material_code" :column :m.code}
   {:key :location/code :header "location_code" :column :l.code}
   {:key :location/name :header "location_name" :column :l.name}
   ;; Computed after the query, from the location id selected alongside.
   {:key :location/path :header "location_path"}])

;; No export options -- there is nothing optional to toggle, unlike material's
;; taxon and accession columns.
(def export-options [])

(def ^:private Params
  [:map
   [:q {:default ""} :string]])

(defn handler
  "Export observations as CSV."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db material-separator timezone]} context
        {:keys [q]} (params/decode Params query-params)
        ast (search.i/parse q)

        base-stmt {:select (conj (vec (keep :column columns)) :l.id)
                   :from [[:observation :o]]
                   :join-by [:left [[:material :m]
                                    [:and [:= :o.resource_type "material"] [:= :m.id :o.resource_id]]]
                             :left [[:accession :acc] [:= :acc.id :m.accession_id]]
                             :left [[:location :l]
                                    [:and [:= :o.resource_type "location"] [:= :l.id :o.resource_id]]]]}

        stmt (-> (search.i/compile-query :observation ast base-stmt
                                         {:material-separator material-separator})
                 (assoc :order-by [[:o.observed_on :desc] [:o.id :desc]]))
        rows (let [rows (db.i/execute! db stmt)
                   paths (location-path/by-id db (keep :location/id rows))]
               (mapv #(assoc % :location/path (get paths (:location/id %))) rows))

        csv-content (csv/rows->csv columns rows)
        filename (csv/export-filename "observations" timezone)]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
