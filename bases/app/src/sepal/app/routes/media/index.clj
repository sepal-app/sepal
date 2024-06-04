(ns sepal.app.routes.media.index
  (:require [lambdaisland.uri :as uri]
            [reitit.core :as r]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]))

(defn thumbnail-url [host key]
  (uri/uri-str {:scheme "https"
                :host host
                :path (str "/" key)
                :query (uri/map->query-string {:max-h 300 :max-w 300 :fit "crop"})}))

(defn media-item [& {:keys [url]}]
  [:li {:class "relative"}
   [:div {:class (html/attr "group" "aspect-w-10" "aspect-h-7" "block" "w-full"
                            "overflow-hidden" "rounded-lg" "bg-gray-100" "shadow-lg"
                            "focus-within:ring-2" "focus-within:ring-indigo-500"
                            "focus-within:ring-offset-2" "focus-within:ring-offset-gray-100")}
    [:img {:class "pointer-events-none object-cover group-hover:opacity-75"
           :src url}]
    [:button {:type "button"
              :class "absolute inset-0 focus:outline-none"}]]])

(defn media-list [& {:keys [thumbnail-urls]}]
  [:ul {:id "media-list"
        :class (html/attr "grid" "grid-cols-2" "gap-x-4" "gap-y-8" "sm:grid-cols-3"
                          "sm:gap-x-6" "lg:grid-cols-4" "xl:gap-x-8")}
   (for [url thumbnail-urls]
     (media-item :url url))])

(defn title-buttons []
  [:button {:id "upload-button"
            :class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                              "border" "border-transparent" "bg-indigo-600" "px-4" "py-2"
                              "text-sm" "font-medium" "text-white" "shadow-sm"
                              "hover:bg-indigo-700" "focus:outline-none" "focus:ring-2"
                              "focus:ring-indigo-500" "focus:ring-offset-2" "sm:w-auto")}
   "Upload"])

(defn page-content [& {:keys [org router thumbnail-urls]}]
  [:div
   [:link {:rel "stylesheet"
           :href (html/static-url "css/media.css")}]
   [:div {:id "media-page"}
    [:div {:x-media-uploader (json/js {:antiForgeryToken *anti-forgery-token*
                                       :signingUrl (url-for router :media/s3)
                                       :organizationId (:organization/id org)
                                       :trigger "#upload-button"})}]
    (media-list :thumbnail-urls thumbnail-urls)
    [:div {:id "upload-success-forms"
           :class "hidden"}]]
   [:script {:type "module"
             :src (html/static-url "js/media.ts")}]])

(defn render [& {:keys [href org page-num page-size router rows thumbnail-urls total]}]
  (-> (page/page :page-title "Media"
                 :page-title-buttons (title-buttons)
                 :content (page-content :org org
                                        :thumbnail-urls thumbnail-urls
                                        :router router)
                 :router router)
      (html/render-html)))

(defn handler [& {:keys [context ::r/router request-method] :as _request}]
  (let [{:keys [db current-organization imgix-media-domain]} context
        media (db.i/execute! db {:select :*
                                 :from :media
                                 :where [:= :organization_id (:organization/id current-organization)]
                                 :order-by [[:created-at :desc]]})
        thumbnail-urls (mapv #(thumbnail-url imgix-media-domain (:media/s3-key %)) media)]
    (case request-method
      :post
      (let []
        (render :route router))

      (render :thumbnail-urls thumbnail-urls
              :org current-organization
              :router router))))
