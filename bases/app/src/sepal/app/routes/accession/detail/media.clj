(ns sepal.app.routes.accession.detail.media
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.detail.tabs :as accession.tabs]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.tabs :as tabs]
            [sepal.media.interface :as media.i]
            [zodiac.core :as z]))

(defn title-buttons []
  [:button {:id "upload-button"
            :class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                              "border" "border-transparent" "bg-indigo-600" "px-4" "py-2"
                              "text-sm" "font-medium" "text-white" "shadow-sm"
                              "hover:bg-indigo-700" "focus:outline-none" "focus:ring-2"
                              "focus:ring-indigo-500" "focus:ring-offset-2" "sm:w-auto")}
   "Upload"])

(defn next-page-url [& {:keys [accession current-page]}]
  (z/url-for accession.routes/detail-media
             {:id (:accession/id accession)}
             {:page (+ 1 current-page)}))

(defn page-content [& {:keys [media page page-size accession]}]
  [:div {:x-data (json/js {:selected nil})
         :class "flex flex-col gap-8"}

   (tabs/tabs2 (accession.tabs/items :accession accession
                                     :active :media))

   [:link {:rel "stylesheet"
           :href (html/static-url "app/routes/media/css/media.css")}]
   [:div {:id "media-page"}
    ;; TODO: This won't work b/c its reusing the anti forgery token. We should
    ;; probably store the antiForgeryToken in a separate element and then that
    ;; element can be updated with the when we get the signing urls
    [:div {:x-media-uploader (json/js {:antiForgeryToken (force *anti-forgery-token*)
                                       :signingUrl (z/url-for media.routes/s3)
                                       :linkResourceType "accession"
                                       :linkResourceId (:accession/id accession)
                                       :trigger "#upload-button"})}]
    (media.ui/media-list :media media
                         :next-page-url (when (>= (count media) page-size)
                                          (next-page-url :accession accession
                                                         :current-page page)))
    [:div {:id "upload-success-forms"
           :class "hidden"}]]
   [:script {:type "module"
             :src (html/static-url "app/routes/media/media.ts")}]])

(defn render [& {:keys [page page-size media accession]}]
  (page/page :page-title "Media"
             :page-title-buttons (title-buttons)
             :content (page-content :page page
                                    :page-size page-size
                                    :media media
                                    :accession accession)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 10} :int]])

(defn handler [{:keys [::z/context htmx-boosted? htmx-request? query-params]}]
  (let [{:keys [db imgix-media-domain resource]} context
        {:keys [page page-size]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        limit page-size
        media (->> (media.i/get-linked db
                                       "accession"
                                       (:accession/id resource)
                                       :offset offset
                                       :limit limit)
                   (mapv #(assoc % :thumbnail-url (media.ui/thumbnail-url imgix-media-domain
                                                                          (:media/s3-key %)))))]

    ;; TODO: if a media instance is unlinked then we need to remove it from the
    ;; resource media list page

    (if (and htmx-request? (not htmx-boosted?))
      (-> (media.ui/media-list-items :media media
                                     :next-page-url (when (>= (count media) page-size)
                                                      (next-page-url :accession resource
                                                                     :current-page page))
                                     :page page)
          (html/render-partial))
      (render :media media
              :page 1
              :page-size page-size
              :accession resource))))
