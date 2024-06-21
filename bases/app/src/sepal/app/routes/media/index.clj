(ns sepal.app.routes.media.index
  (:require [lambdaisland.uri :as uri]
            [reitit.core :as r]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]))

(defn thumbnail-url [host key]
  (uri/uri-str {:scheme "https"
                :host host
                :path (str "/" key)
                :query (uri/map->query-string {:max-h 300 :max-w 300 :fit "crop"})}))

(defn media-item [& {:keys [router item load-page-on-reveal]}]
  [:li (cond-> {:class "relative"}
         (pos-int? load-page-on-reveal)
         (merge {:hx-get (url-for router
                                  :org/media
                                  {:org-id (:media/organization-id item)}
                                  {:page load-page-on-reveal})
                 :hx-trigger "revealed"
                 :hx-target "#media-list"
                 :hx-swap "beforeend"}))
   [:div {:class (html/attr "group" "aspect-w-10" "aspect-h-7" "block" "w-full"
                            "overflow-hidden" "rounded-lg" "bg-gray-100" "shadow-lg"
                            "focus-within:ring-2" "focus-within:ring-indigo-500"
                            "focus-within:ring-offset-2" "focus-within:ring-offset-gray-100")}
    [:a {:href (url-for router :media/detail {:id (:media/id item)})
         :class "inset-0 focus:outline-none"}
     [:img {:class "pointer-events-none object-cover group-hover:opacity-75"
            :src (:thumbnail-url item)}]]]])

(defn media-list-items [& {:keys [page media router]}]
  (map-indexed (fn [idx m]
                 (media-item :item m
                             :router router
                             :load-page-on-reveal (when (= idx (- (count media) 1))
                                                    (+ 1 page))))
               media))

(defn title-buttons []
  [:button {:id "upload-button"
            :class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                              "border" "border-transparent" "bg-indigo-600" "px-4" "py-2"
                              "text-sm" "font-medium" "text-white" "shadow-sm"
                              "hover:bg-indigo-700" "focus:outline-none" "focus:ring-2"
                              "focus:ring-indigo-500" "focus:ring-offset-2" "sm:w-auto")}
   "Upload"])

(defn page-content [& {:keys [media org page router]}]
  [:div {:x-data (json/js {:selected nil})}
   [:link {:rel "stylesheet"
           :href (html/static-url "css/media.css")}]
   [:div {:id "media-page"}
    ;; TODO: This won't work b/c its reusing the anti forgery token. We should
    ;; probably store the antiForgeryToken in a separate element and then that
    ;; element can be updated with the when we get the signing urls
    [:div {:x-media-uploader (json/js {:antiForgeryToken (force *anti-forgery-token*)
                                       :signingUrl (url-for router :media/s3)
                                       :organizationId (:organization/id org)
                                       :trigger "#upload-button"})}]
    [:ul {:id "media-list"
          :class (html/attr "grid" "grid-cols-2" "gap-x-4" "gap-y-8" "sm:grid-cols-3"
                            "sm:gap-x-6" "lg:grid-cols-4" "xl:gap-x-8")}
     (media-list-items :router router
                       :media media
                       :page page)]
    [:div {:id "upload-success-forms"
           :class "hidden"}]]
   [:script {:type "module"
             :src (html/static-url "js/media.ts")}]])

(defn render [& {:keys [org page router media]}]
  (-> (page/page :page-title "Media"
                 :page-title-buttons (title-buttons)
                 :content (page-content :org org
                                        :page page
                                        :media media
                                        :router router)
                 :router router)
      (html/render-html)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 10} :int]])

(defn handler [& {:keys [context htmx-boosted? htmx-request? query-params ::r/router] :as _request}]
  (let [{:keys [db current-organization imgix-media-domain]} context
        {:keys [page page-size]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        media (->> (db.i/execute! db {:select :*
                                      :from :media
                                      :where [:= :organization_id (:organization/id current-organization)]
                                      :limit page-size
                                      :offset offset
                                      :order-by [[:created-at :desc]]})
                   (mapv #(assoc %
                                 :thumbnail-url (thumbnail-url imgix-media-domain (:media/s3-key %)))))]

    (if (and htmx-request? (not htmx-boosted?))
      (-> (media-list-items :router router
                            :media media
                            :page page)
          (html/render-partial))
      ;; (media-list)
      (render :media media
              :page page
              :org current-organization
              :router router))))
