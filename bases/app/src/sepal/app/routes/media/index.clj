(ns sepal.app.routes.media.index
  (:require [lambdaisland.uri :as uri]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as ui.page]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(defn thumbnail-url [host key]
  (uri/uri-str {:scheme "https"
                :host host
                :path (str "/" key)
                :query (uri/map->query-string {:max-h 300 :max-w 300 :fit "crop"})}))

(defn title-buttons []
  [:button {:id "upload-button"
            :class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                              "border" "border-transparent" "bg-indigo-600" "px-4" "py-2"
                              "text-sm" "font-medium" "text-white" "shadow-sm"
                              "hover:bg-indigo-700" "focus:outline-none" "focus:ring-2"
                              "focus:ring-indigo-500" "focus:ring-offset-2" "sm:w-auto")}
   "Upload"])

(defn next-page-url [& {:keys [current-page]}]
  (z/url-for media.routes/index nil {:page (+ 1 current-page)}))

(defn page-content [& {:keys [media page page-size]}]
  (ui.page/page-inner
    [:div {:x-data (json/js {:selected nil})}
     [:link {:rel "stylesheet"
             :href (html/static-url "app/routes/media/css/media.css")}]
     [:div {:id "media-page"}
      ;; TODO: This won't work b/c its reusing the anti forgery token. We should
      ;; probably store the antiForgeryToken in a separate element and then that
      ;; element can be updated with the when we get the signing urls
      [:div {:x-media-uploader (json/js {:antiForgeryToken (force *anti-forgery-token*)
                                         :signingUrl (z/url-for media.routes/s3)
                                         :trigger "#upload-button"})}]
      (media.ui/media-list :media media
                           :next-page-url (when (>= (count media) page-size)
                                            (next-page-url :current-page page))
                           :page page)
      [:div {:id "upload-success-forms"
             :class "hidden"}]]
     [:script {:type "module"
               :src (html/static-url "app/routes/media/media.ts")}]]))

(defn render [& {:keys [page page-size media]}]
  (ui.page/page :page-title "Media"
                :page-title-buttons (title-buttons)
                :content (page-content :page page
                                       :page-size page-size
                                       :media media)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 20} :int]])

(defn handler [& {:keys [::z/context htmx-boosted? htmx-request? query-params] :as _request}]
  (let [{:keys [db imgix-media-domain]} context
        {:keys [page page-size]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        media (->> (db.i/execute! db {:select :*
                                      :from :media
                                      :limit page-size
                                      :offset offset
                                      :order-by [[:created-at :desc]]})
                   (mapv #(assoc %
                                 :thumbnail-url (thumbnail-url imgix-media-domain (:media/s3-key %)))))]

    (if (and htmx-request? (not htmx-boosted?))
      (-> (media.ui/media-list-items :media media
                                     :next-page-url (when (>= (count media) page-size)
                                                      (next-page-url :current-page page)))
          (html/render-partial))
      (render :media media
              :page page
              :page-size page-size))))
