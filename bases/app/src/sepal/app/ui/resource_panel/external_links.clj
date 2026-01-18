(ns sepal.app.ui.resource-panel.external-links
  "External link URL generators and UI components for taxonomic resources."
  (:require [lambdaisland.uri :as uri]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.resource-panel :as panel]))

(defn wfo-plantlist-search-url
  "Generate WFO Plantlist search URL for a taxon name."
  [taxon-name]
  (when taxon-name
    (uri/uri-str {:scheme "https"
                  :host "wfoplantlist.org"
                  :path "/search"
                  :query (uri/map->query-string {:query taxon-name})})))

(defn wfo-plantlist-taxon-url
  "Generate WFO Plantlist direct URL for a WFO taxon ID.
   Extracts the name ID from the full taxon ID (wfo-NNNNNNNNNN-YYYY-MM -> wfo-NNNNNNNNNN)."
  [wfo-taxon-id]
  (when wfo-taxon-id
    (let [name-id (second (re-find #"^(wfo-\d{10})" wfo-taxon-id))]
      (str "https://wfoplantlist.org/taxon/" name-id))))

(defn iucn-redlist-url
  "Generate IUCN Red List search URL for a taxon name."
  [taxon-name]
  (when taxon-name
    (uri/uri-str {:scheme "https"
                  :host "www.iucnredlist.org"
                  :path "/search"
                  :query (uri/map->query-string {:query taxon-name})})))

(defn cites-checklist-url
  "Generate CITES Checklist search URL for a taxon name."
  [taxon-name]
  (when taxon-name
    (str "https://checklist.cites.org/#/en/search/"
         (uri/map->query-string {:output_layout "alphabetical"
                                 :level_of_listing 0
                                 :show_synonyms 1
                                 :show_author 1
                                 :scientific_name taxon-name}))))

(defn taxonomic-links-section
  "Render external links section for taxonomic resources.
   Uses the taxon name to generate search URLs for WFO, IUCN, and CITES.
   
   Options:
   - :taxon-name   - The taxon name for search URLs (required)
   - :wfo-taxon-id - Optional WFO taxon ID for direct link instead of search"
  [& {:keys [taxon-name wfo-taxon-id]}]
  (let [wfo-url (or (wfo-plantlist-taxon-url wfo-taxon-id)
                    (wfo-plantlist-search-url taxon-name))]
    (panel/external-links-section
      :links [{:label "WFO Plantlist"
               :href wfo-url
               :icon (lucide/globe :class "w-4 h-4")}
              {:label "IUCN Red List"
               :href (iucn-redlist-url taxon-name)
               :icon (lucide/globe :class "w-4 h-4")}
              {:label "CITES Checklist"
               :href (cites-checklist-url taxon-name)
               :icon (lucide/globe :class "w-4 h-4")}])))
