(ns sepal.app.routes.material.panel
  "Resource panel content for materials.
   Displays material summary, linked resources, history, and activity."
  (:require [clojure.string :as str]
            [sepal.accession.interface :as acc.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.app.ui.resource-panel.external-links :as external-links]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn- format-material-type
  "Format material type keyword for display."
  [material-type]
  (when material-type
    (-> (name material-type)
        (str/replace "-" " ")
        (str/capitalize))))

(defn- change-card
  "One history card: localized datetime, signed delta, movement, reason."
  [change timezone]
  (let [{:material-change/keys [changed-at quantity]} change
        from (some-> change :from-location :location/name)
        to (some-> change :to-location :location/name)
        label (:material-change-reason/label change)]
    [:div {:class "spl-card bg-surface shadow-sm"}
     [:div {:class "spl-card-body p-3"}
      [:div {:class "flex items-center justify-between"}
       (datetime/datetime (datetime/sqlite-datetime->instant changed-at)
                          timezone
                          :class "text-sm text-text-soft")
       [:span {:class "text-sm font-medium"}
        (format "%+d" quantity)]]
      [:div {:class "text-sm"}
       (cond
         (and from to) (str from " → " to)
         to (str "to " to)
         from (str "from " from)
         :else "quantity change")]
      (when label
        [:div {:class "text-sm text-text-soft"} label])]]))

(defn history-cards
  "The change rows as cards, newest first. Shared by the panel section and
  the Show all fragment."
  [history timezone]
  [:div {:class "space-y-2"}
   (for [change history]
     ^{:key (:material-change/id change)}
     (change-card change timezone))])

(defn- history-section
  "Three newest changes, with a Show all button when there are more."
  [material history timezone]
  (panel/collapsible-section
    :title "History"
    :count (count history)
    :disabled? (empty? history)
    :empty-label "no recorded changes"
    :default-open? (seq history)
    :children
    (list
      (history-cards (take 3 history) timezone)
      (when (> (count history) 3)
        [:button {:class "spl-btn spl-btn--ghost spl-btn--sm w-full"
                  :hx-get (z/url-for material.routes/history
                                     {:id (:material/id material)})
                  :hx-target "closest .spl-collapse-content"
                  :hx-swap "innerHTML"}
         (format "Show all (%d)" (count history))]))))

(defn panel-content
  "Render the material panel content.

   Options:
   - :material       - The material map
   - :accession      - The associated accession map
   - :taxon          - The associated taxon map
   - :location       - The associated location map
   - :history        - Change rows with :from-location and :to-location maps
   - :activities     - Recent activities for this material
   - :activity-count - Total activity count
   - :timezone       - Timezone string for formatting timestamps
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [material accession taxon location history
             activities activity-count timezone on-close]}]
  (let [{:material/keys [code material-type quantity status]} material
        sci-name (:taxon/name taxon)]
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
                                    :class "spl-link"}
                                (:accession/code accession)])}
                     {:label "Taxon"
                      :value (when taxon
                               [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
                                    :class "spl-link"}
                                (taxon-name/render sci-name)])}
                     {:label "Location"
                      :value (when location
                               [:a {:href (z/url-for location.routes/detail {:id (:location/id location)})
                                    :class "spl-link"}
                                (:location/name location)])}]))

        ;; History section
        (history-section material history timezone)

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

(defn- history-for
  "A material's change rows with their from/to locations attached."
  [db material-id]
  (mapv (fn [change]
          (assoc change
                 :from-location
                 (when-let [id (:material-change/from-location-id change)]
                   (loc.i/get-by-id db id))
                 :to-location
                 (when-let [id (:material-change/to-location-id change)]
                   (loc.i/get-by-id db id))))
        (mat.i/list-by-material-id db material-id)))

(defn fetch-panel-data
  "Fetch all data needed for the material panel.
   Returns a map with :material, :accession, :taxon, :location, :history,
   :activities, :activity-count."
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
     :history (history-for db material-id)
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for material panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource timezone]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :material (:material panel-data)
        :accession (:accession panel-data)
        :taxon (:taxon panel-data)
        :location (:location panel-data)
        :history (:history panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)
        :timezone timezone))))

(defn history-handler
  "Handler for the Show all fragment: every change card, newest first."
  [{:keys [::z/context]}]
  (let [{:keys [db resource timezone]} context]
    (html/render-partial
      (history-cards (history-for db (:material/id resource)) timezone))))
