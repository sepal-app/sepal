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
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.material.interface :as mat.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn- wfo-plantlist-url
  "Generate WFO Plantlist URL for a taxon."
  [wfo-taxon-id]
  (when wfo-taxon-id
    ;; Extract the name ID from the taxon ID (wfo-NNNNNNNNNN-YYYY-MM -> wfo-NNNNNNNNNN)
    (let [name-id (second (re-find #"^(wfo-\d{10})" wfo-taxon-id))]
      (str "https://wfoplantlist.org/taxon/" name-id))))

(defn- cites-checklist-url
  "Generate CITES Checklist search URL for a taxon name."
  [taxon-name]
  (str "https://checklist.cites.org/#/en/search/"
       "output_layout=alphabetical"
       "&level_of_listing=0"
       "&show_synonyms=1"
       "&show_author=1"
       "&scientific_name="
       (java.net.URLEncoder/encode taxon-name "UTF-8")))

(defn- format-rank
  "Format rank keyword for display."
  [rank]
  (when rank
    (-> (name rank)
        (str/replace "-" " ")
        (str/capitalize))))

(defn panel-content
  "Render the taxon panel content.

   Options:
   - :taxon      - The taxon map
   - :parent     - Optional parent taxon map
   - :stats      - Map with :accession-count, :material-count
   - :activities - Recent activities for this taxon
   - :activity-count - Total activity count
   - :on-close   - Optional close handler (for list page)"
  [& {:keys [taxon parent stats activities activity-count on-close]}]
  (let [{:taxon/keys [id name author rank wfo-taxon-id]} taxon
        {:keys [accession-count material-count]} stats
        wfo-url (wfo-plantlist-url wfo-taxon-id)
        cites-url (cites-checklist-url name)]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title [:em name]
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
                                         :class "text-primary hover:underline"}
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

        ;; External links section
        (panel/collapsible-section
          :title "External Links"
          :disabled? (not wfo-url)
          :empty-label "no WFO ID"
          :children
          (panel/external-links-section
            :links [{:label "WFO Plantlist"
                     :href wfo-url
                     :icon (lucide/globe :class "w-4 h-4")}
                    {:label "CITES Checklist"
                     :href cites-url
                     :icon (lucide/globe :class "w-4 h-4")}]))

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
  "Fetch all data needed for the taxon panel.
   Returns a map with :taxon, :parent, :stats, :activities, :activity-count."
  [db taxon]
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
                                                     :resource-id taxon-id)]
    {:taxon taxon
     :parent parent
     :stats {:accession-count accession-count
             :material-count material-count}
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for taxon panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :taxon (:taxon panel-data)
        :parent (:parent panel-data)
        :stats (:stats panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)))))
