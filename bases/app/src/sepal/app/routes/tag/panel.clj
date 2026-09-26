(ns sepal.app.routes.tag.panel
  "Resource panel content for tags.
   Displays tag summary, linked record counts, and activity."
  (:require [sepal.activity.interface :as activity.i]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.tag.interface :as tag.i]
            [zodiac.core :as z]))

(defn- search-href [index-route tag-name]
  (z/url-for index-route nil {:q (str "tag:\"" tag-name "\"")}))

(defn panel-content
  "Render the tag panel content.

   Options:
   - :tag            - The tag map
   - :stats          - Map with :taxon-count, :accession-count, :material-count
   - :activities     - Recent activities for this tag
   - :activity-count - Total activity count
   - :timezone       - Timezone string for formatting timestamps"
  [& {:keys [tag stats activities activity-count timezone]}]
  (let [{:tag/keys [name description]} tag
        {:keys [taxon-count accession-count material-count]} stats
        link-count (+ taxon-count accession-count material-count)]
    (panel/panel-container
      :children
      (list
        (panel/panel-header :title name)

        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Name" :value name}
                     {:label "Description" :value description}]))

        ;; The search filter matches names containing the text, so a tag whose
        ;; name is part of another's counts exactly here but not in the list.
        (panel/collapsible-section
          :title "Linked records"
          :count link-count
          :disabled? (zero? link-count)
          :empty-label "none"
          :children
          (panel/statistics-section
            :stats [{:label "Taxa"
                     :value taxon-count
                     :href (search-href taxon.routes/index name)}
                    {:label "Accessions"
                     :value accession-count
                     :href (search-href accession.routes/index name)}
                    {:label "Materials"
                     :value material-count
                     :href (search-href material.routes/index name)}]))

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
  "Fetch all data needed for the tag panel.
   Returns a map with :tag, :stats, :activities, :activity-count."
  [db tag]
  (let [tag-id (:tag/id tag)
        counts (frequencies (map :tag-link/resource-type (tag.i/get-tagged db tag-id)))]
    {:tag tag
     :stats {:taxon-count (get counts "taxon" 0)
             :accession-count (get counts "accession" 0)
             :material-count (get counts "material" 0)}
     :activities (activity.i/get-by-resource db
                                             :resource-type :tag
                                             :resource-id tag-id
                                             :limit 5)
     :activity-count (activity.i/count-by-resource db
                                                   :resource-type :tag
                                                   :resource-id tag-id)}))
