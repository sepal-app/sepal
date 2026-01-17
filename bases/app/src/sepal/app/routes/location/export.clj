(ns sepal.app.routes.location.export
  "CSV export handler for locations."
  (:require [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.database.interface :as db.i]
            [sepal.location.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(def ^:private columns
  "Location columns for export."
  [{:key :location/id :header "location_id" :column :l.id}
   {:key :location/code :header "location_code" :column :l.code}
   {:key :location/name :header "location_name" :column :l.name}
   {:key :location/description :header "location_description" :column :l.description}])

;; No export options for location - simple resource
(def export-options [])

(def ^:private Params
  [:map
   [:q {:default ""} :string]])

(defn handler
  "Export locations as CSV."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        {:keys [q]} (params/decode Params query-params)
        ast (search.i/parse q)

        base-stmt {:select (mapv :column columns)
                   :from [[:location :l]]}

        stmt (-> (search.i/compile-query :location ast base-stmt)
                 (assoc :order-by [:l.code]))
        rows (db.i/execute! db stmt)

        csv-content (csv/rows->csv columns rows)
        filename (format "locations-%s.csv"
                         (.format (LocalDateTime/now)
                                  (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")))]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
