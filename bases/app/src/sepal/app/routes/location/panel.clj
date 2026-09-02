(ns sepal.app.routes.location.panel
  "Resource panel content for locations.
   Displays location summary, statistics, linked resources, and activity."
  (:require [sepal.activity.interface :as activity.i]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.material.interface :as mat.i]
            [zodiac.core :as z]))

(defn panel-content
  "Render the location panel content.

   Options:
   - :location       - The location map
   - :stats          - Map with :material-count
   - :moved-out      - Change rows whose material left this location
   - :activities     - Recent activities for this location
   - :activity-count - Total activity count
   - :timezone       - Timezone string for formatting timestamps
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [location stats moved-out activities activity-count timezone on-close]}]
  (let [{:location/keys [id name code description]} location
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
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Name" :value name}
                     {:label "Code" :value code}
                     {:label "Description" :value description}]))

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
                     :href (z/url-for material.routes/index nil {:location-id id})}]))

        ;; Moved section
        (panel/collapsible-section
          :title "Moved"
          :count (count moved-out)
          :disabled? (empty? moved-out)
          :empty-label "nothing has moved"
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
                (if-let [to (:location/name row)]
                  (str "to " to)
                  "removed")]]])])

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
  "Fetch all data needed for the location panel.
   Returns a map with :location, :stats, :moved-out, :activities,
   :activity-count."
  [db location]
  (let [location-id (:location/id location)
        material-count (mat.i/count-by-location-id db location-id)
        moved-out (mat.i/moved-out-by-location-id db location-id)
        activities (activity.i/get-by-resource db
                                               :resource-type :location
                                               :resource-id location-id
                                               :limit 5)
        activity-count (activity.i/count-by-resource db
                                                     :resource-type :location
                                                     :resource-id location-id)]
    {:location location
     :stats {:material-count material-count}
     :moved-out moved-out
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for location panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource timezone]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :location (:location panel-data)
        :stats (:stats panel-data)
        :moved-out (:moved-out panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)
        :timezone timezone))))
