(ns sepal.app.ui.media
  (:require [lambdaisland.uri :as uri]
            [sepal.app.html :as html]
            [sepal.app.routes.media.routes :as media.routes]
            [zodiac.core :as z]))

(defn media-item [& {:keys [item next-page-url]}]
  ;; TODO: Make sure that item has a :thumbnail-url key
  [:li (cond-> {:class "relative"}
         (some? next-page-url)
         (merge {:hx-get next-page-url
                 :hx-trigger "revealed"
                 :hx-target "#media-list"
                 :hx-swap "beforeend"}))
   [:div {:class (html/attr "group" "aspect-w-10" "aspect-h-7" "block" "w-full"
                            "overflow-hidden" "rounded-lg" "bg-gray-100" "shadow-lg"
                            "focus-within:ring-2" "focus-within:ring-indigo-500"
                            "focus-within:ring-offset-2" "focus-within:ring-offset-gray-100")}
    [:a {:href (z/url-for media.routes/detail {:id (:media/id item)})
         :class "inset-0 focus:outline-none"}
     [:img {:class "pointer-events-none object-cover group-hover:opacity-75"
            :src (:thumbnail-url item)}]]]])

(defn media-list-items [& {:keys [media next-page-url]}]
  (map-indexed (fn [idx m]
                 (media-item :item m
                             :next-page-url (when (= idx (- (count media) 1))
                                              next-page-url)))
               media))

(defn media-list [& {:keys [media next-page-url]}]
  [:ul {:id "media-list"
        :class (html/attr "grid" "grid-cols-2" "gap-x-4" "gap-y-8" "sm:grid-cols-3"
                          "sm:gap-x-6" "lg:grid-cols-4" "xl:gap-x-8")}
   (media-list-items :media media
                     :next-page-url next-page-url)
   (when (zero? (count media))
     [:div {:class "flex items-center justify-center bg-blue-50 p-6 rounded-xl"}
      [:span "No media"]])])

#_(defn upload-button []
    [:button {:id "upload-button"
              :class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                                "border" "border-transparent" "bg-indigo-600" "px-4" "py-2"
                                "text-sm" "font-medium" "text-white" "shadow-sm"
                                "hover:bg-indigo-700" "focus:outline-none" "focus:ring-2"
                                "focus:ring-indigo-500" "focus:ring-offset-2" "sm:w-auto")}
     "Upload"])

(defn thumbnail-url
  "Generate a thumbnail URL for a media item."
  [media-id & {:keys [w h fit] :or {w 300 h 300 fit "crop"}}]
  (str (z/url-for media.routes/transform {:id media-id})
       "?" (uri/map->query-string {:w w :h h :fit fit})))
