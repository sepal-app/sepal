(ns sepal.app.routes.location.panel
  "Resource panel content for locations.
   Displays location summary, statistics, linked resources, and activity."
  (:require [sepal.activity.interface :as activity.i]
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
   - :activities     - Recent activities for this location
   - :activity-count - Total activity count
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [location stats activities activity-count on-close]}]
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
                     :href (z/url-for material.routes/index {:location-id id})}]))

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
  "Fetch all data needed for the location panel.
   Returns a map with :location, :stats, :activities, :activity-count."
  [db location]
  (let [location-id (:location/id location)
        material-count (mat.i/count-by-location-id db location-id)
        activities (activity.i/get-by-resource db
                                               :resource-type :location
                                               :resource-id location-id
                                               :limit 5)
        activity-count (activity.i/count-by-resource db
                                                     :resource-type :location
                                                     :resource-id location-id)]
    {:location location
     :stats {:material-count material-count}
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for location panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :location (:location panel-data)
        :stats (:stats panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)))))
