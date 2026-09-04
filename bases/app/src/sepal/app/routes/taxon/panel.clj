(ns sepal.app.routes.taxon.panel
  "Resource panel content for taxa.
   Displays taxon summary, statistics, external links, and activity."
  (:require [clojure.string :as str]
            [sepal.accession.interface :as acc.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.app.ui.resource-panel.external-links :as external-links]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.material.interface :as mat.i]
            [sepal.synonym.interface :as synonym.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn- format-rank
  "Format rank keyword for display."
  [rank]
  (when rank
    (-> (name rank)
        (str/replace "-" " ")
        (str/capitalize))))

(defn- synonyms-section
  "The names this taxon is also known by.

   A synonym is a name, not a resource, so these are not links — there is
   nothing to navigate to. WFO-sourced rows carry a badge because they come from
   the shared reference file rather than from this garden: nobody here asserted
   them and they cannot be removed from the Synonyms tab. `local` and `imported`
   rows are the garden's own and need no marker."
  [& {:keys [synonyms]}]
  [:div {:class "space-y-0"}
   (for [{:synonym/keys [synonym-name source]} synonyms]
     ^{:key (str source "-" synonym-name)}
     [:div {:class "flex items-center gap-2 text-sm -mx-2 px-2 py-1.5"}
      (taxon-name/render synonym-name)
      (when (= "wfo" source)
        [:span {:class "spl-badge spl-badge--neutral"} "WFO"])])])

(defn panel-content
  "Render the taxon panel content.

   Options:
   - :taxon          - The taxon map
   - :parent         - Optional parent taxon map
   - :stats          - Map with :accession-count, :material-count
   - :synonyms       - The taxon's synonyms, garden rows and WFO rows merged
   - :activities     - Recent activities for this taxon
   - :activity-count - Total activity count
   - :timezone       - Timezone string for formatting timestamps
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [taxon parent stats synonyms activities activity-count timezone on-close]}]
  (let [{:taxon/keys [id name author rank wfo-taxon-id]} taxon
        {:keys [accession-count material-count]} stats]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title (taxon-name/render name)
          :subtitle (when author author)
          :on-close on-close)

        ;; Summary section
        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields (cond-> [{:label "Rank" :value (format-rank rank)}
                             {:label "Author" :value author}]
                      parent
                      (conj {:label "Parent"
                             :value [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id parent)})
                                         :class "spl-link"}
                                     (:taxon/name parent)]})
                      true
                      (conj {:label "WFO ID" :value wfo-taxon-id}))))

        ;; Statistics section
        (panel/collapsible-section
          :title "Statistics"
          :count (+ (or accession-count 0) (or material-count 0))
          :children
          (panel/statistics-section
            :stats [{:label "Accessions"
                     :value accession-count
                     :href (z/url-for accession.routes/index nil {:taxon-id id})}
                    {:label "Materials"
                     :value material-count
                     :href (z/url-for material.routes/index nil {:taxon-id id})}]))

        ;; Synonyms section. Disabled rather than absent when empty, matching
        ;; External Links and Activity below — a section that vanishes makes the
        ;; panel's shape vary between taxa.
        (panel/collapsible-section
          :title "Synonyms"
          :count (count synonyms)
          :disabled? (empty? synonyms)
          :empty-label "none"
          :default-open? false
          :children
          (synonyms-section :synonyms synonyms))

        ;; External links section
        (panel/collapsible-section
          :title "External Links"
          :disabled? (not wfo-taxon-id)
          :empty-label "no WFO ID"
          :children
          (external-links/taxonomic-links-section
            :taxon-name name
            :wfo-taxon-id wfo-taxon-id))

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
  "Fetch all data needed for the taxon panel.
   Returns a map with :taxon, :parent, :stats, :synonyms, :activities,
   :activity-count.

   Takes the request context as well as the database because synonyms come from
   two places: the garden's own `taxon_synonym` table, which is above the
   supported schema floor and so must be gated, and the shared read-only WFO
   reference file, which is opened once per process. `ctx` carries the
   `:schema-version` for the gate and the `:synonym-reference` pool."
  [ctx db taxon]
  (let [taxon-id (:taxon/id taxon)
        parent (when-let [parent-id (:taxon/parent-id taxon)]
                 (taxon.i/get-by-id db parent-id))
        accession-count (acc.i/count-by-taxon-id db taxon-id)
        material-count (mat.i/count-by-taxon-id db taxon-id)
        activities (activity.i/get-by-resource db
                                               :resource-type :taxon
                                               :resource-id taxon-id
                                               :limit 5)
        activity-count (activity.i/count-by-resource db
                                                     :resource-type :taxon
                                                     :resource-id taxon-id)
        synonyms (synonym.i/list-for-taxon ctx db taxon-id)]
    {:taxon taxon
     :parent parent
     :stats {:accession-count accession-count
             :material-count material-count}
     :synonyms synonyms
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for taxon panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource timezone]} context
        panel-data (fetch-panel-data context db resource)]
    (html/render-partial
      (panel-content
        :taxon (:taxon panel-data)
        :parent (:parent panel-data)
        :stats (:stats panel-data)
        :synonyms (:synonyms panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)
        :timezone timezone))))
