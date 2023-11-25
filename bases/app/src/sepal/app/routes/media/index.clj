(ns sepal.app.routes.media.index
  (:require [lambdaisland.uri :as uri]
            [reitit.core :as r]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.html :as html]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.page :as page]
            [sepal.aws-s3.interface :as aws-s3.i]))


(defn thumbnail-url [host key]
  (uri/uri-str {:scheme "https"
                :host host
                :path (str "/" key)
                :query (uri/map->query-string {:max-h 300 :max-w 300 :fit "crop"})}))

(defn media-item [& {:keys [image]}]
  [:li {:class "relative"}
   [:div {:class (html/attr "group" "aspect-w-10" "aspect-h-7" "block" "w-full"
                            "overflow-hidden" "rounded-lg" "bg-gray-100"
                            "focus-within:ring-2" "focus-within:ring-indigo-500"
                            "focus-within:ring-offset-2" "focus-within:ring-offset-gray-100") }
    [:img {:class "pointer-events-none object-cover group-hover:opacity-75"
           :src image}]
    [:button {:type "button"
              :class "absolute inset-0 focus:outline-none"}]]])

(defn media-list [& {:keys [images]}]
  [:ul {:id "media-list"
        :class (html/attr "grid" "grid-cols-2" "gap-x-4" "gap-y-8" "sm:grid-cols-3"
                          "sm:gap-x-6" "lg:grid-cols-4" "xl:gap-x-8")}
   (for [image images]
     (media-item :image image))])

(defn title-buttons []
  [:button {:id "upload-button"
            :class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                              "border" "border-transparent" "bg-indigo-600" "px-4" "py-2"
                              "text-sm" "font-medium" "text-white" "shadow-sm"
                              "hover:bg-indigo-700" "focus:outline-none" "focus:ring-2"
                              "focus:ring-indigo-500" "focus:ring-offset-2" "sm:w-auto")}
   "Upload"])

(defn page-content [& {:keys [images org router s3-objects]}]
  [:div
   [:link {:rel "stylesheet"
           ;; The media.css file is in the js/ folder b/c vite builds it from
           ;; the imports in the media.ts file.
           :href (html/static-url "js/media.css")}]
   [:div {:id "media-page"}
    [:media-uploader {:anti-forgery-token *anti-forgery-token*
                      :signing-url "/media/s3" ;; TODO: Lookup in router
                      :organization-id (:organization/id org)
                      :trigger "#upload-button"}]
    (media-list :images images)
    [:div {:id "upload-success-forms"
           :class "hidden"}]]
   [:script {:type "module"
             :src (html/static-url "js/media.ts")}]])

(defn render [& {:keys [href images org page-num page-size router rows total]}]
  (-> (page/page :page-title "Media"
                 :page-title-buttons (title-buttons)
                 :content (page-content :images images
                                        :org org
                                        :router router)
                 :router router)
      (html/render-html)))

(defn handler [& {:keys [context headers query-params ::r/router request-method uri] :as _request}]
  (let [{:keys [current-organization imgix-media-domain media-upload-bucket s3-client]} context
        ;; TODO: Get the objects from the medial table
        s3-objects (aws-s3.i/list-objects s3-client
                                          media-upload-bucket
                                          (format "organization_id=%s/"
                                                  (:organization/id current-organization)))
        fetch-metadata (fn [key]
                         (-> (aws-s3.i/head-object s3-client media-upload-bucket key)
                             (assoc :key key)))
        images (mapv #(thumbnail-url imgix-media-domain (:key %)) s3-objects)
        metadata (doall (pmap #(fetch-metadata (:key %)) s3-objects))]

    (case request-method
      :post
      (let []
        ;; (morse/inspect _request)
        (render :route router))

      (render :images images
              :org current-organization
              :route router))))
