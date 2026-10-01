(ns sepal.app.routes.location.panel
  "Resource panel content for locations.
   Displays location summary, statistics, linked resources, and activity."
  (:require [sepal.accession.interface :as acc.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.propagation.shared :as propagation.shared]
            [sepal.app.ui.location-path :as location-path]
            [sepal.app.ui.propagations :as ui.propagations]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.i18n.interface :refer [tr trc]]
            [sepal.location.interface :as loc.i]
            [sepal.material.interface :as mat.i]
            [sepal.propagation.interface :as propagation.i]
            [zodiac.core :as z]))

(defn panel-content
  "Render the location panel content.

   Options:
   - :location       - The location map
   - :ancestors      - Its ancestors, root first
   - :children       - The locations directly inside it
   - :stats          - Map with :material-count
   - :awaiting       - Accessions intended for this location, nothing planted yet
   - :moved-out      - Change rows whose material left this location
   - :activities     - Recent activities for this location
   - :activity-count - Total activity count
   - :timezone       - Timezone string for formatting timestamps
   - :on-close       - Optional close handler (for list page)"
  [& {:as opts}]
  (let [{:keys [location ancestors children stats awaiting moved-out activities
                activity-count timezone on-close propagations type-labels
                status-labels]}
        (merge (:panel-data opts) opts)
        {:location/keys [id name code description]} location
        {:keys [material-count]} stats]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title name
          :subtitle code
          :on-close on-close)

        ;; Summary section
        (panel/collapsible-section
          :title (tr "Summary")
          :children
          (panel/summary-section
            :fields [{:label (tr "Name") :value name}
                     {:label (trc "location" "Code") :value code}
                     {:label (trc "location" "Parent")
                      :value (when-let [parent (last ancestors)]
                               [:a {:href (z/url-for location.routes/detail
                                                     {:id (:location/id parent)})
                                    :class "spl-link"}
                                (location-path/markup ancestors)])}
                     {:label (tr "Description") :value description}]))

        ;; Sub-locations: the ones directly inside this one. Deeper levels are
        ;; a click away on each. Adding one is in the record's Actions menu.
        (panel/collapsible-section
          :title (tr "Sub-locations")
          :count (count children)
          :disabled? (empty? children)
          :empty-label (trc "empty section" "none")
          :children
          [:div {:class "space-y-1"}
           (for [child children]
             ^{:key (:location/id child)}
             [:div {:class "text-sm"}
              [:a {:href (z/url-for location.routes/detail {:id (:location/id child)})
                   :class "spl-link"}
               (:location/name child)]
              " "
              [:span {:class "text-text-soft"} (:location/code child)]])])

        ;; Statistics section
        (panel/collapsible-section
          :title (tr "Statistics")
          :count material-count
          :disabled? (zero? (or material-count 0))
          :empty-label (trc "empty section" "none")
          :children
          (panel/statistics-section
            :stats [{:label (tr "Material")
                     :value material-count
                     :href (z/url-for material.routes/index nil {:location-id id})}]))

        ;; Awaiting planting section
        (panel/collapsible-section
          :title (tr "Awaiting planting")
          :count (count awaiting)
          :disabled? (empty? awaiting)
          :empty-label (tr "nothing waiting")
          :children
          [:div {:class "space-y-2"}
           (for [row awaiting]
             ^{:key (:accession/id row)}
             [:div {:class "spl-card bg-surface shadow-sm"}
              [:div {:class "spl-card-body p-3"}
               [:div {:class "flex items-center justify-between"}
                [:a {:href (z/url-for accession.routes/detail {:id (:accession/id row)})
                     :class "spl-link text-sm font-medium"}
                 (:accession/code row)]
                [:a {:href (z/url-for material.routes/new nil
                                      {:accession-id (:accession/id row)})
                     :class "spl-link text-sm"}
                 (tr "Plant here")]]
               [:div {:class "text-sm"} (:taxon/name row)]
               (when-let [received (:accession/date-received row)]
                 [:div {:class "text-sm text-text-soft"} (tr "received %1" received)])]])])

        ;; Moved section
        (panel/collapsible-section
          :title (tr "Moved")
          :count (count moved-out)
          :disabled? (empty? moved-out)
          :empty-label (tr "nothing has moved")
          :default-open? false
          :children
          [:div {:class "space-y-2"}
           (for [row moved-out]
             ^{:key (:material-change/id row)}
             [:div {:class "spl-card bg-surface shadow-sm"}
              [:div {:class "spl-card-body p-3"}
               [:div {:class "flex items-center justify-between"}
                [:span {:class "text-sm font-medium"} (:material/code row)]
                (datetime/datetime
                  (datetime/sqlite-datetime->instant
                    (:material-change/changed-at row))
                  timezone
                  :class "text-sm text-text-soft")]
               [:div {:class "text-sm"}
                (if-let [to (:to-path row)]
                  (tr "to %1" to)
                  (tr "removed"))]]])])

        ;; Propagations running here: what is on the bench alongside the
        ;; material filed at it.
        (ui.propagations/panel-section
          :propagations propagations
          :type-labels type-labels
          :status-labels status-labels
          :empty-label (tr "nothing running here"))

        ;; Activity section
        (panel/collapsible-section
          :title (tr "Activity")
          :count activity-count
          :disabled? (zero? (or activity-count 0))
          :empty-label (trc "empty section" "none")
          :default-open? false
          :children
          (panel/activity-section
            :activities activities
            :total-count activity-count
            :timezone timezone))))))

(defn fetch-panel-data
  "Fetch all data needed for the location panel.
   Returns a map with :location, :stats, :awaiting, :moved-out, :activities,
   :activity-count."
  [db location]
  (let [location-id (:location/id location)
        chain (get (loc.i/paths db #{location-id}) location-id)
        ;; Everything below covers the location and its sub-locations, apart
        ;; from Activity, which is about the location record itself.
        subtree (loc.i/subtree [:= :l.id location-id])
        material-count (mat.i/count-in-locations db subtree)
        awaiting (acc.i/awaiting-planting-in-locations db subtree)
        moved-out (let [rows (mat.i/moved-out-of-locations db subtree)
                        paths (location-path/by-id db (keep :material-change/to-location-id rows))]
                    (mapv #(assoc % :to-path (get paths (:material-change/to-location-id %))) rows))
        activities (activity.i/get-by-resource db
                                               :resource-type :location
                                               :resource-id location-id
                                               :limit 5)
        activity-count (activity.i/count-by-resource db
                                                     :resource-type :location
                                                     :resource-id location-id)
        ;; The worklist: a completed or failed batch is history, not something
        ;; on the bench. The raw row holds the stored string, not the keyword.
        propagations (filter #(= "active" (name (:propagation/status %)))
                             (propagation.i/list-in-locations db subtree))]
    {:location location
     :ancestors (vec (butlast chain))
     :children (loc.i/list-children db location-id)
     :stats {:material-count material-count}
     :awaiting awaiting
     :moved-out moved-out
     :activities activities
     :activity-count activity-count
     :propagations propagations
     :type-labels (propagation.shared/type-labels db)
     :status-labels (propagation.shared/status-labels db)}))

(defn handler
  "Handler for location panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource timezone]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :panel-data panel-data
        :location (:location panel-data)
        :stats (:stats panel-data)
        :awaiting (:awaiting panel-data)
        :moved-out (:moved-out panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)
        :timezone timezone))))
