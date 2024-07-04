(ns sepal.app.routes.taxon.detail.media
  (:require [reitit.core :as r]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.tabs :as tabs]
            [sepal.media.interface :as media.i]))

(defn title-buttons []
  [:button {:id "upload-button"
            :class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                              "border" "border-transparent" "bg-indigo-600" "px-4" "py-2"
                              "text-sm" "font-medium" "text-white" "shadow-sm"
                              "hover:bg-indigo-700" "focus:outline-none" "focus:ring-2"
                              "focus:ring-indigo-500" "focus:ring-offset-2" "sm:w-auto")}
   "Upload"])

(defn next-page-url [& {:keys [router taxon current-page]}]
  (url-for router
           :taxon/detail-media
           {:id (:taxon/id taxon)}
           {:page (+ 1 current-page)}))

(defn tab-items [& {:keys [router taxon]}]
  [{:label "Name"
    :key :name
    :href (url-for router :taxon/detail-name {:id (:taxon/id taxon)})}
   {:label "Media"
    :key :media
    :href (url-for router :taxon/detail-media {:id (:taxon/id taxon)})}])

(defn page-content [& {:keys [media org page page-size router taxon]}]
  [:div {:x-data (json/js {:selected nil})
         :class "flex flex-col gap-8"}

   (tabs/tabs :active :media
              :items (tab-items :router router :taxon taxon))

   [:link {:rel "stylesheet"
           :href (html/static-url "css/media.css")}]
   [:div {:id "media-page"}
    ;; TODO: This won't work b/c its reusing the anti forgery token. We should
    ;; probably store the antiForgeryToken in a separate element and then that
    ;; element can be updated with the when we get the signing urls
    [:div {:x-media-uploader (json/js {:antiForgeryToken (force *anti-forgery-token*)
                                       :signingUrl (url-for router :media/s3)
                                       :organizationId (:organization/id org)
                                       :linkResourceType "taxon"
                                       :linkResourceId (:taxon/id taxon)
                                       :trigger "#upload-button"})}]
    (media.ui/media-list :router router
                         :media media
                         :next-page-url (when (>= (count media) page-size)
                                          (next-page-url :router router
                                                         :taxon taxon
                                                         :current-page page)))
    [:div {:id "upload-success-forms"
           :class "hidden"}]]
   [:script {:type "module"
             :src (html/static-url "js/media.ts")}]])

(defn render [& {:keys [org page page-size router media taxon]}]
  (-> (page/page :page-title "Media"
                 :page-title-buttons (title-buttons)
                 :content (page-content :org org
                                        :page page
                                        :page-size page-size
                                        :media media
                                        :router router
                                        :taxon taxon)
                 :router router)
      (html/render-html)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 10} :int]])

(defn handler [{:keys [context htmx-boosted? htmx-request? query-params ::r/router]}]
  (let [{:keys [db imgix-media-domain organization resource]} context
        {:keys [page page-size]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        limit page-size
        media (->> (media.i/get-linked db
                                       "taxon"
                                       (:taxon/id resource)
                                       (:organization/id organization)
                                       :offset offset
                                       :limit limit)
                   (mapv #(assoc %
                                 :thumbnail-url (media.ui/thumbnail-url imgix-media-domain
                                                                        (:media/s3-key %)))))]

    ;; TODO: if a media instance is unlinked then we need to remove it from the
    ;; resource media list page

    ;; TODO: Need to make sure the media are owned by the organization
    (if (and htmx-request? (not htmx-boosted?))
      (-> (media.ui/media-list-items :router router
                                     :media media
                                     :next-page-url (when (>= (count media) page-size)
                                                      (next-page-url :router router
                                                                     :taxon resource
                                                                     :current-page page))
                                     :page page)
          (html/render-partial))
      (render :org organization
              :media media
              :page 1
              :page-size page-size
              :router router
              :taxon resource))))
