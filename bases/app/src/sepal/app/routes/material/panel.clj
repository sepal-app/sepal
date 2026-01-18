(ns sepal.app.routes.material.panel
  "Resource panel content for materials.
   Displays material summary, linked resources, and activity."
  (:require [clojure.string :as str]
            [sepal.accession.interface :as acc.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.resource-panel.external-links :as external-links]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.location.interface :as loc.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn- format-material-type
  "Format material type keyword for display."
  [material-type]
  (when material-type
    (-> (name material-type)
        (str/replace "-" " ")
        (str/capitalize))))

(defn panel-content
  "Render the material panel content.

   Options:
   - :material       - The material map
   - :accession      - The associated accession map
   - :taxon          - The associated taxon map
   - :location       - The associated location map
   - :activities     - Recent activities for this material
   - :activity-count - Total activity count
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [material accession taxon location activities activity-count on-close]}]
  (let [{:material/keys [code material-type quantity status]} material
        taxon-name (:taxon/name taxon)]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title code
          :subtitle (when accession (:accession/code accession))
          :on-close on-close)

        ;; Summary section
        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Code" :value code}
                     {:label "Type" :value (format-material-type material-type)}
                     {:label "Quantity" :value quantity}
                     {:label "Status" :value (when status (str/capitalize (name status)))}
                     {:label "Accession"
                      :value (when accession
                               [:a {:href (z/url-for accession.routes/detail {:id (:accession/id accession)})
                                    :class "text-primary hover:underline"}
                                (:accession/code accession)])}
                     {:label "Taxon"
                      :value (when taxon
                               [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
                                    :class "text-primary hover:underline"}
                                [:em taxon-name]])}
                     {:label "Location"
                      :value (when location
                               [:a {:href (z/url-for location.routes/detail {:id (:location/id location)})
                                    :class "text-primary hover:underline"}
                                (:location/name location)])}]))

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
  "Fetch all data needed for the material panel.
   Returns a map with :material, :accession, :taxon, :location, :activities, :activity-count."
  [db material]
  (let [material-id (:material/id material)
        accession (when-let [accession-id (:material/accession-id material)]
                    (acc.i/get-by-id db accession-id))
        taxon (when-let [taxon-id (:accession/taxon-id accession)]
                (taxon.i/get-by-id db taxon-id))
        location (when-let [location-id (:material/location-id material)]
                   (loc.i/get-by-id db location-id))
        activities (activity.i/get-by-resource db
                                               :resource-type :material
                                               :resource-id material-id
                                               :limit 5)
        activity-count (activity.i/count-by-resource db
                                                     :resource-type :material
                                                     :resource-id material-id)]
    {:material material
     :accession accession
     :taxon taxon
     :location location
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for material panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :material (:material panel-data)
        :accession (:accession panel-data)
        :taxon (:taxon panel-data)
        :location (:location panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)))))
