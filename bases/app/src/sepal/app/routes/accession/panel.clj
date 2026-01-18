(ns sepal.app.routes.accession.panel
  "Resource panel content for accessions.
   Displays accession summary, statistics, linked resources, and activity."
  (:require [clojure.string :as str]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.app.ui.resource-panel.external-links :as external-links]
            [sepal.contact.interface :as contact.i]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn- format-provenance-type
  "Format provenance type keyword for display."
  [provenance-type]
  (when provenance-type
    (-> (name provenance-type)
        (str/replace "-" " ")
        (str/capitalize))))

(defn panel-content
  "Render the accession panel content.

   Options:
   - :accession      - The accession map
   - :taxon          - The associated taxon map
   - :supplier       - Optional supplier contact map
   - :stats          - Map with :material-count
   - :activities     - Recent activities for this accession
   - :activity-count - Total activity count
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [accession taxon supplier stats activities activity-count on-close]}]
  (let [{:accession/keys [id code provenance-type]} accession
        {:keys [material-count]} stats
        taxon-name (:taxon/name taxon)]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title code
          :subtitle (when taxon [:em taxon-name])
          :on-close on-close)

        ;; Summary section
        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Code" :value code}
                     {:label "Taxon"
                      :value (when taxon
                               [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
                                    :class "text-primary hover:underline"}
                                [:em taxon-name]])}
                     {:label "Provenance" :value (format-provenance-type provenance-type)}
                     {:label "Supplier"
                      :value (when supplier
                               [:a {:href (z/url-for contact.routes/detail {:id (:contact/id supplier)})
                                    :class "text-primary hover:underline"}
                                (:contact/name supplier)])}]))

        ;; Statistics section
        (panel/collapsible-section
          :title "Statistics"
          :count material-count
          :disabled? (zero? (or material-count 0))
          :empty-label "none"
          :children
          (panel/statistics-section
            :stats [{:label "Materials"
                     :value material-count
                     :href (z/url-for material.routes/index nil {:accession-id id})}]))

        ;; External links section
        (panel/collapsible-section
          :title "External Links"
          :children
          (external-links/taxonomic-links-section :taxon-name taxon-name))

        ;; Activity section
        (panel/collapsible-section
          :title "Activity"
          :count activity-count
          :disabled? (zero? (or activity-count 0))
          :empty-label "none"
          :default-open? false
          :children
          (panel/activity-section
            :activities activities
            :total-count activity-count))))))

(defn fetch-panel-data
  "Fetch all data needed for the accession panel.
   Returns a map with :accession, :taxon, :supplier, :stats, :activities, :activity-count."
  [db accession]
  (let [accession-id (:accession/id accession)
        taxon (when-let [taxon-id (:accession/taxon-id accession)]
                (taxon.i/get-by-id db taxon-id))
        supplier (when-let [supplier-id (:accession/supplier-contact-id accession)]
                   (contact.i/get-by-id db supplier-id))
        material-count (mat.i/count-by-accession-id db accession-id)
        activities (activity.i/get-by-resource db
                                               :resource-type :accession
                                               :resource-id accession-id
                                               :limit 5)
        activity-count (activity.i/count-by-resource db
                                                     :resource-type :accession
                                                     :resource-id accession-id)]
    {:accession accession
     :taxon taxon
     :supplier supplier
     :stats {:material-count material-count}
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for accession panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :accession (:accession panel-data)
        :taxon (:taxon panel-data)
        :supplier (:supplier panel-data)
        :stats (:stats panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)))))
