(ns sepal.app.routes.material.detail.media
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.accession.interface :as accession.i]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.material.detail.shared :as material.shared]
            [sepal.app.routes.material.panel :as material.panel]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn title-buttons []
  (media.ui/upload-button))

(defn next-page-url [& {:keys [material current-page]}]
  (z/url-for material.routes/detail-media
             {:id (:material/id material)}
             {:page (+ 1 current-page)}))

(defn page-content [& {:keys [media page page-size material accession taxon]}]
  (material.shared/page
    :material material
    :accession accession
    :taxon taxon
    :active material.shared/media-tab
    :body
    [:div {:x-data (json/js {:selected nil})}
     [:link {:rel "stylesheet"
             :href (html/static-url "app/routes/media/css/media.css")}]
     [:div {:id "media-page"}
    ;; TODO: This won't work b/c its reusing the anti forgery token. We should
    ;; probably store the antiForgeryToken in a separate element and then that
    ;; element can be updated with the when we get the signing urls
      [:div {:x-media-uploader (json/js {:antiForgeryToken (force *anti-forgery-token*)
                                         :signingUrl (z/url-for media.routes/s3)
                                         :linkResourceType "material"
                                         :linkResourceId (:material/id material)
                                         :trigger "#upload-button"})}]
      (media.ui/media-list :media media
                           :next-page-url (when (>= (count media) page-size)
                                            (next-page-url :material material
                                                           :current-page page)))
      [:div {:id "upload-success-forms"
             :class "hidden"}]]
     [:script {:type "module"
               :src (html/static-url "app/routes/media/media.ts")}]]))

(defn render [& {:keys [accession page page-size media material taxon panel-data]}]
  (ui.page/page
    :content (pages.detail/page-content-with-panel
               :content (page-content :page page
                                      :page-size page-size
                                      :media media
                                      :material material
                                      :accession accession
                                      :taxon taxon)
               :panel-content (material.panel/panel-content
                                :material (:material panel-data)
                                :accession (:accession panel-data)
                                :taxon (:taxon panel-data)
                                :location (:location panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)))
    :breadcrumbs (material.shared/breadcrumbs :accession accession
                                              :material material
                                              :taxon taxon)
    :page-title-buttons (title-buttons)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 10} :int]])

(defn handler [{:keys [::z/context htmx-boosted? htmx-request? query-params]}]
  (let [{:keys [db resource]} context
        {:keys [page page-size]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        limit page-size
        accession (accession.i/get-by-id db (:material/accession-id resource))
        taxon (taxon.i/get-by-id db (:accession/taxon-id accession))
        media (->> (media.i/get-linked db
                                       "material"
                                       (:material/id resource)
                                       :offset offset
                                       :limit limit)
                   (mapv #(assoc % :thumbnail-url (media.ui/thumbnail-url (:media/id %)))))]

    ;; TODO: if a media instance is unlinked then we need to remove it from the
    ;; resource media list page

    (if (and htmx-request? (not htmx-boosted?))
      (-> (media.ui/media-list-items :media media
                                     :next-page-url (when (>= (count media) page-size)
                                                      (next-page-url :material resource
                                                                     :current-page page))
                                     :page page)
          (html/render-partial))
      (let [panel-data (material.panel/fetch-panel-data db resource)]
        (render :accession accession
                :media media
                :page 1
                :page-size page-size
                :material resource
                :taxon taxon
                :panel-data panel-data)))))
