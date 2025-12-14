(ns sepal.app.routes.media.panel
  "Resource panel content for media.
   Displays media summary, preview thumbnail, and linked resources."
  (:require [lambdaisland.uri :as uri]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.database.interface :as db.i]
            [sepal.media.interface :as media.i]
            [zodiac.core :as z]))

(defn- link-url
  "Generate URL for a media link based on resource type."
  [link]
  (case (:media-link/resource-type link)
    "accession" (z/url-for accession.routes/detail {:id (:media-link/resource-id link)})
    "location" (z/url-for location.routes/detail {:id (:media-link/resource-id link)})
    "material" (z/url-for material.routes/detail {:id (:media-link/resource-id link)})
    "taxon" (z/url-for taxon.routes/detail {:id (:media-link/resource-id link)})
    nil))

(defn- link-text-query
  "Generate SQL query for link text based on resource type."
  [link]
  (case (:media-link/resource-type link)
    "accession" {:select [[[:concat :a.code " (" :t.name ")"] :text]]
                 :from [[:accession :a]]
                 :join [[:taxon :t] [:= :t.id :a.taxon-id]]
                 :where [:= :a.id (:media-link/resource-id link)]}
    "location" {:select [[[:concat :l.name " (" :l.code ")"] :text]]
                :from [[:location :l]]
                :where [:= :l.id (:media-link/resource-id link)]}
    "material" {:select [[[:concat :a.code "." :m.code " (" :t.name ")"] :text]]
                :from [[:material :m]]
                :join [[:accession :a] [:= :a.id :m.accession_id]
                       [:taxon :t] [:= :t.id :a.taxon-id]]
                :where [:= :m.id (:media-link/resource-id link)]}
    "taxon" {:select [:name]
             :from [:taxon]
             :where [:= :id (:media-link/resource-id link)]}
    nil))

(defn- get-link-info
  "Get display info for a media link."
  [db link]
  (when link
    (let [query (link-text-query link)
          result (when query (db.i/execute-one! db query))
          text (or (:text result) (:name result))]
      {:text text
       :url (link-url link)
       :type (:media-link/resource-type link)})))

(defn- thumbnail-url
  "Generate thumbnail URL for media preview."
  [imgix-host s3-key]
  (uri/uri-str {:scheme "https"
                :host imgix-host
                :path (str "/" s3-key)
                :query (uri/map->query-string {:max-h 200 :max-w 200 :fit "crop"})}))

(defn panel-content
  "Render the media panel content.

   Options:
   - :media           - The media map
   - :thumbnail-url   - URL for thumbnail preview
   - :link-info       - Map with :text, :url, :type for linked resource
   - :on-close        - Optional close handler (for list page)"
  [& {:keys [media thumbnail-url link-info on-close]}]
  (let [{:media/keys [title content-type]} media]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title (or title "Untitled")
          :subtitle content-type
          :on-close on-close)

        ;; Preview section
        (when thumbnail-url
          (panel/collapsible-section
            :title "Preview"
            :children
            [:div {:class "flex justify-center p-2"}
             [:img {:src thumbnail-url
                    :class "max-h-48 rounded shadow"
                    :alt title}]]))

        ;; Summary section
        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Title" :value title}
                     {:label "Type" :value content-type}
                     {:label "Linked to"
                      :value (when link-info
                               [:a {:href (:url link-info)
                                    :class "text-primary hover:underline"}
                                (:text link-info)])}]))))))

(defn fetch-panel-data
  "Fetch all data needed for the media panel.
   Returns a map with :media, :thumbnail-url, :link-info."
  [db imgix-media-domain media]
  (let [link (media.i/get-link db (:media/id media))
        link-info (get-link-info db link)]
    {:media media
     :thumbnail-url (thumbnail-url imgix-media-domain (:media/s3-key media))
     :link-info link-info}))

(defn handler
  "Handler for media panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db imgix-media-domain resource]} context
        panel-data (fetch-panel-data db imgix-media-domain resource)]
    (html/render-partial
      (panel-content
        :media (:media panel-data)
        :thumbnail-url (:thumbnail-url panel-data)
        :link-info (:link-info panel-data)))))
