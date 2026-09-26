(ns sepal.app.routes.location.detail.media
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.location.detail.shared :as location.shared]
            [sepal.app.routes.location.panel :as location.panel]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.media.link-info :as link-info]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.media.interface :as media.i]
            [zodiac.core :as z]))

(defn next-page-url [& {:keys [location current-page below?]}]
  (z/url-for location.routes/detail-media
             {:id (:location/id location)}
             (cond-> {:page (+ 1 current-page)}
               below? (assoc :scope "below"))))

(defn page-content [& {:keys [below? media page page-size location]}]
  (location.shared/page
    :location location
    :active location.shared/media-tab
    :body
    [:div {:x-data (json/js {:selected nil})}
     [:link {:rel "stylesheet"
             :href (html/static-url "app/routes/media/css/media.css")}]
     [:div {:id "media-page"}
      [:div {:x-media-uploader (json/js {:antiForgeryToken (force *anti-forgery-token*)
                                         :signingUrl (z/url-for media.routes/s3)
                                         :linkResourceType "location"
                                         :linkResourceId (:location/id location)
                                         :trigger "#upload-button"})}]
      ;; A location is where material is, not what it is, so its material's
      ;; media is opt-in rather than part of the location's own.
      (media.ui/media-list :context :record
                           :filters (media.ui/scope-toggle :action (z/url-for location.routes/detail-media
                                                                              {:id (:location/id location)})
                                                           :below? below?
                                                           :hint "Media linked to the material in this location")
                           :media media
                           :next-page-url (when (>= (count media) page-size)
                                            (next-page-url :location location
                                                           :current-page page
                                                           :below? below?)))
      [:div {:id "upload-success-forms"
             :class "hidden"}]]]))

(defn render [& {:keys [below? page page-size media location panel-data timezone]}]
  (ui.page/page :page-title-buttons (media.ui/upload-button)
                :content (pages.detail/page-content-with-panel
                           :content (page-content :below? below?
                                                  :page page
                                                  :page-size page-size
                                                  :media media
                                                  :location location)
                           :panel-content (location.panel/panel-content
                                            :panel-data panel-data
                                            :location (:location panel-data)
                                            :stats (:stats panel-data)
                                            :awaiting (:awaiting panel-data)
                                            :moved-out (:moved-out panel-data)
                                            :activities (:activities panel-data)
                                            :activity-count (:activity-count panel-data)
                                            :timezone timezone))
                :breadcrumbs (location.shared/breadcrumbs location)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 10} :int]
   [:scope {:optional true} [:enum "below"]]])

(defn handler [{:keys [::z/context htmx-boosted? htmx-request? query-params]}]
  (let [{:keys [db material-separator resource timezone]} context
        {:keys [page page-size scope]} (params/decode Params query-params)
        below? (= "below" scope)
        media (->> (media.i/get-linked db
                                       "location"
                                       (:location/id resource)
                                       :scope (if below? :below :direct)
                                       :offset (* page-size (- page 1))
                                       :limit page-size)
                   (link-info/with-via db material-separator "location" (:location/id resource))
                   (mapv #(assoc % :thumbnail-url (media.ui/thumbnail-url (:media/id %)))))]
    (if (and htmx-request? (not htmx-boosted?))
      (-> (media.ui/media-list-items :media media
                                     :next-page-url (when (>= (count media) page-size)
                                                      (next-page-url :location resource
                                                                     :current-page page
                                                                     :below? below?)))
          (html/render-partial))
      (render :below? below?
              :media media
              :page 1
              :page-size page-size
              :location resource
              :panel-data (location.panel/fetch-panel-data db resource)
              :timezone timezone))))
