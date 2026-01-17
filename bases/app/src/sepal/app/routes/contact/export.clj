(ns sepal.app.routes.contact.export
  "CSV export handler for contacts."
  (:require [sepal.app.csv :as csv]
            [sepal.app.params :as params]
            [sepal.contact.interface.search]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]))

(def ^:private columns
  "Contact columns for export."
  [{:key :contact/id :header "contact_id" :column :c.id}
   {:key :contact/name :header "contact_name" :column :c.name}
   {:key :contact/email :header "contact_email" :column :c.email}
   {:key :contact/address :header "contact_address" :column :c.address}
   {:key :contact/province :header "contact_province" :column :c.province}
   {:key :contact/postal-code :header "contact_postal_code" :column :c.postal_code}
   {:key :contact/country :header "contact_country" :column :c.country}
   {:key :contact/phone :header "contact_phone" :column :c.phone}
   {:key :contact/business :header "contact_business" :column :c.business}
   {:key :contact/notes :header "contact_notes" :column :c.notes}])

;; No export options for contact - simple resource
(def export-options [])

(def ^:private Params
  [:map
   [:q {:default ""} :string]])

(defn handler
  "Export contacts as CSV."
  [& {:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        {:keys [q]} (params/decode Params query-params)
        ast (search.i/parse q)

        base-stmt {:select (mapv :column columns)
                   :from [[:contact :c]]}

        stmt (-> (search.i/compile-query :contact ast base-stmt)
                 (assoc :order-by [:c.name]))
        rows (db.i/execute! db stmt)

        csv-content (csv/rows->csv columns rows)
        filename (format "contacts-%s.csv"
                         (.format (LocalDateTime/now)
                                  (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")))]

    {:status 200
     :headers {"Content-Type" "text/csv; charset=utf-8"
               "Content-Disposition" (format "attachment; filename=\"%s\"" filename)}
     :body csv-content}))
