(ns sepal.app.routes.accession.panel
  "Resource panel content for accessions.
   Displays accession summary, statistics, linked resources, and activity."
  (:require [clojure.string :as str]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.notes :as ui.notes]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.app.ui.resource-panel.external-links :as external-links]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.contact.interface :as contact.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as mat.i]
            [sepal.note.interface :as note.i]
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
   - :intended-location - Optional location its material is meant for
   - :stats          - Map with :material-count
   - :notes          - Recent notes for this accession
   - :note-count     - Total note count
   - :activities     - Recent activities for this accession
   - :activity-count - Total activity count
   - :timezone       - Timezone string for formatting timestamps
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [accession taxon supplier intended-location stats notes note-count
             activities activity-count timezone on-close]}]
  (let [{:accession/keys [id code provenance-type received-type quantity-received]} accession
        {:keys [material-count]} stats
        sci-name (:taxon/name taxon)]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title code
          :subtitle (when taxon (taxon-name/render sci-name))
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
                                    :class "spl-link"}
                                (taxon-name/render sci-name)])}
                     {:label "Provenance" :value (format-provenance-type provenance-type)}
                     {:label "Supplier"
                      :value (when supplier
                               [:a {:href (z/url-for contact.routes/detail {:id (:contact/id supplier)})
                                    :class "spl-link"}
                                (:contact/name supplier)])}
                     {:label "Intended location"
                      :value (when intended-location
                               [:a {:href (z/url-for location.routes/detail
                                                     {:id (:location/id intended-location)})
                                    :class "spl-link"}
                                (:location/name intended-location)])}
                     {:label "Received as"
                      :value (some-> received-type accession.form/enum-label-fn)}
                     {:label "Quantity received" :value quantity-received}]))

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

        ;; Notes section
        (panel/collapsible-section
          :title "Notes"
          :count note-count
          :disabled? (zero? (or note-count 0))
          :empty-label "none"
          :default-open? false
          :children
          (ui.notes/panel-section
            :notes notes
            :note-count note-count
            :more-url (z/url-for accession.routes/detail-notes {:id id})))

        ;; External links section
        (panel/collapsible-section
          :title "External Links"
          :children
          (external-links/taxonomic-links-section :taxon-name sci-name))

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
            :total-count activity-count
            :timezone timezone))))))

(defn fetch-panel-data
  "Fetch all data needed for the accession panel.
   Returns a map with :accession, :taxon, :supplier, :intended-location,
   :stats, :notes,
   :note-count, :activities, :activity-count."
  [db accession]
  (let [accession-id (:accession/id accession)
        taxon (when-let [taxon-id (:accession/taxon-id accession)]
                (taxon.i/get-by-id db taxon-id))
        supplier (when-let [supplier-id (:accession/supplier-contact-id accession)]
                   (contact.i/get-by-id db supplier-id))
        intended-location (when-let [location-id (:accession/intended-location-id accession)]
                            (location.i/get-by-id db location-id))
        material-count (mat.i/count-by-accession-id db accession-id)
        notes (take 3 (note.i/get-for-resource db :accession accession-id))
        note-count (note.i/count-for-resource db :accession accession-id)
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
     :intended-location intended-location
     :stats {:material-count material-count}
     :notes notes
     :note-count note-count
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for accession panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource timezone]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :accession (:accession panel-data)
        :taxon (:taxon panel-data)
        :supplier (:supplier panel-data)
        :intended-location (:intended-location panel-data)
        :stats (:stats panel-data)
        :notes (:notes panel-data)
        :note-count (:note-count panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)
        :timezone timezone))))
