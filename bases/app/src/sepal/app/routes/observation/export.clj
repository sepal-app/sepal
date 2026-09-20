(ns sepal.app.routes.observation.export
  "CSV export handler for observations."
  (:require [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.database.interface :as db.i]
            [sepal.observation.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

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
   {:key :location/name :header "location_name" :column :l.name}])

;; No export options -- there is nothing optional to toggle, unlike material's
;; taxon and accession columns.
(def export-options [])

(def ^:private Params
  [:map
   [:q {:default ""} :string]])

(defn handler
  "Export observations as CSV."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        {:keys [q]} (params/decode Params query-params)
        ast (search.i/parse q)

        base-stmt {:select (mapv :column columns)
                   :from [[:observation :o]]
                   :join-by [:left [[:material :m]
                                    [:and [:= :o.resource_type "material"] [:= :m.id :o.resource_id]]]
                             :left [[:accession :acc] [:= :acc.id :m.accession_id]]
                             :left [[:location :l]
                                    [:and [:= :o.resource_type "location"] [:= :l.id :o.resource_id]]]]}

        stmt (-> (search.i/compile-query :observation ast base-stmt)
                 (assoc :order-by [[:o.observed_on :desc] [:o.id :desc]]))
        rows (db.i/execute! db stmt)

        csv-content (csv/rows->csv columns rows)
        filename (format "observations-%s.csv"
                         (.format (LocalDateTime/now)
                                  (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")))]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
