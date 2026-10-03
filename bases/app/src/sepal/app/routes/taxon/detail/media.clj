(ns sepal.app.routes.taxon.detail.media
  (:require [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.media.link-info :as link-info]
            [sepal.app.routes.taxon.detail.shared :as taxon.shared]
            [sepal.app.routes.taxon.panel :as taxon.panel]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.i18n.interface :refer [tr]]
            [sepal.media.interface :as media.i]
            [zodiac.core :as z]))

(defn next-page-url [& {:keys [taxon current-page below?]}]
  (z/url-for taxon.routes/detail-media
             {:id (:taxon/id taxon)}
             (cond-> {:page (+ 1 current-page)}
               below? (assoc :scope "below"))))

(defn page-content [& {:keys [below? media page page-size taxon]}]
  (taxon.shared/page
    :taxon taxon
    :active taxon.shared/media-tab
    :body
    [:div {:x-data (json/js {:selected nil})}
     [:link {:rel "stylesheet"
             :href (html/static-url "app/routes/media/css/media.css")}]
     [:div {:id "media-page"}
      (media.ui/uploader :link-resource-type "taxon"
                         :link-resource-id (:taxon/id taxon))
      (media.ui/media-list :context :record
                           :filters (media.ui/scope-toggle :action (z/url-for taxon.routes/detail-media {:id (:taxon/id taxon)})
                                                           :below? below?
                                                           :hint (tr "Media linked to the taxa, accessions and material below this taxon"))
                           :media media
                           :next-page-url (when (>= (count media) page-size)
                                            (next-page-url :taxon taxon
                                                           :current-page page
                                                           :below? below?)))]]))

(defn render [& {:keys [below? page page-size media taxon panel-data timezone]}]
  (ui.page/page :content (pages.detail/page-content-with-panel
                           :content (page-content :below? below?
                                                  :page page
                                                  :page-size page-size
                                                  :media media
                                                  :taxon taxon)
                           :panel-content (taxon.panel/panel-content
                                            :taxon (:taxon panel-data)
                                            :parent (:parent panel-data)
                                            :stats (:stats panel-data)
                                            :synonyms (:synonyms panel-data)
                                            :notes (:notes panel-data)
                                            :note-count (:note-count panel-data)
                                            :activities (:activities panel-data)
                                            :activity-count (:activity-count panel-data)
                                            :timezone timezone))
                :breadcrumbs (taxon.shared/breadcrumbs taxon)
                :page-title-buttons (taxon.shared/actions
                                      :taxon taxon
                                      :primary (media.ui/upload-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 10} :int]
   [:scope {:optional true} [:enum "below"]]])

(defn handler [{:keys [::z/context htmx-boosted? htmx-request? query-params]}]
  (let [{:keys [db material-separator resource timezone]} context
        {:keys [page page-size scope]} (params/decode Params query-params)
        below? (= "below" scope)
        offset (* page-size (- page 1))
        limit page-size
        media (->> (media.i/get-linked db
                                       "taxon"
                                       (:taxon/id resource)
                                       :scope (if below? :below :direct)
                                       :offset offset
                                       :limit limit)
                   (link-info/with-via db material-separator "taxon" (:taxon/id resource))
                   (mapv #(assoc %
                                 :thumbnail-url (media.ui/thumbnail-url (:media/id %)))))]

    ;; TODO: if a media instance is unlinked then we need to remove it from the
    ;; resource media list page

    ;; TODO: Need to make sure the media are owned by the organization
    (if (and htmx-request? (not htmx-boosted?))
      (-> (media.ui/media-list-items :media media
                                     :next-page-url (when (>= (count media) page-size)
                                                      (next-page-url :taxon resource
                                                                     :current-page page
                                                                     :below? below?))
                                     :page page)
          (html/render-partial))
      (let [panel-data (taxon.panel/fetch-panel-data context db resource)]
        (render :below? below?
                :media media
                :page 1
                :page-size page-size
                :taxon resource
                :panel-data panel-data
                :timezone timezone)))))
