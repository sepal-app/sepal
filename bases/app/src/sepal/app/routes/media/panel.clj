(ns sepal.app.routes.media.panel
  "Resource panel content for media.
   Displays media summary, preview thumbnail, and linked resources."
  (:require [clojure.string :as str]
            [sepal.activity.interface :as activity.i]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.routes.media.link-info :as link-info]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.media.interface :as media.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn panel-content
  "Render the media panel content.

   Options:
   - :media           - The media map
   - :thumbnail-url   - URL for the Preview section; none renders no preview,
                        as on the detail page, where the image is the page
   - :link-info       - Map with :text, :url, :type for linked resource
   - :linked          - Hiccup for the Linked section, replacing the plain
                        link: the editable widget, for an editor
   - :uploader        - The user who uploaded it
   - :activities, :activity-count, :timezone - the Activity section
   - :on-close        - Optional close handler (for list page)"
  [& {:keys [media thumbnail-url link-info linked uploader activities activity-count
             timezone on-close]}]
  (let [{:media/keys [title media-type size-in-bytes created-at]} media]
    (panel/panel-container
      :children
      (list
        (panel/panel-header
          :title (or title "Untitled")
          :subtitle media-type
          :on-close on-close)

        (when thumbnail-url
          (panel/collapsible-section
            :title "Preview"
            :children
            [:div {:class "flex justify-center p-2"}
             [:img {:src thumbnail-url
                    :class "max-h-48 rounded shadow"
                    :alt title}]]))

        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Title" :value title}
                     {:label "Type" :value media-type}
                     {:label "Size" :value (media.ui/format-size size-in-bytes)}
                     {:label "Uploaded"
                      :value (->> [(some-> created-at
                                           datetime/sqlite-datetime->instant
                                           (datetime/format-datetime timezone))
                                   (some->> (:user/email uploader) (str "by "))]
                                  (remove nil?)
                                  (str/join " ")
                                  not-empty)}]))

        (panel/collapsible-section
          :title "Linked"
          :children
          (or linked
              (if link-info
                (if (:url link-info)
                  [:a {:href (:url link-info) :class "spl-link"} (:text link-info)]
                  (:text link-info))
                [:p {:class "text-text-soft text-sm"} "Not linked"])))

        (when activities
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
              :timezone timezone)))))))

(defn fetch-panel-data
  "Fetch all data needed for the media panel."
  [db media separator]
  (let [link (media.i/get-link db (:media/id media))]
    {:media media
     :thumbnail-url (media.ui/thumbnail-url (:media/id media) :w 200 :h 200)
     :link-info (link-info/link-info db link separator)
     :uploader (user.i/get-by-id db (:media/created-by media))
     :activities (activity.i/get-by-resource db
                                             :resource-type :media
                                             :resource-id (:media/id media)
                                             :limit 5)
     :activity-count (activity.i/count-by-resource db
                                                   :resource-type :media
                                                   :resource-id (:media/id media))}))

(defn handler
  "Handler for media panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db material-separator resource timezone]} context
        panel-data (fetch-panel-data db resource material-separator)]
    (html/render-partial
      (panel-content
        :media (:media panel-data)
        :thumbnail-url (:thumbnail-url panel-data)
        :link-info (:link-info panel-data)
        :uploader (:uploader panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)
        :timezone timezone))))
