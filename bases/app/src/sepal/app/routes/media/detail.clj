(ns sepal.app.routes.media.detail
  (:require [lambdaisland.uri :as uri]
            [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as page]))

(defn zoom-view [& {:keys [zoom-url]}]
  [:div {:class "relative z-10"}
   [:div {:x-on:click "console.log('zoom=false'); zoom=false"
          :class "fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"}]
   [:div {:class "fixed inset-0 z-10 w-screen overflow-y-auto"}
    [:div {:class "flex flex-col min-h-full items-end justify-center text-center sm:items-center sm:p-20"}

     [:button {:class "text-white w-full text-right "
               :x-on:click "zoom=false"} "Close"]
     [:div
      [:img {:src zoom-url}]]]]])

(defn page-title-buttons [& {:keys [dl-url]}]
  [:<>
   [:button {:class "btn"
             :aria-label "Zoom"
             :x-on:click "console.log('zoom=true'); zoom=true;"}
    (heroicons/magnifying-glass)]
   [:a {:class "btn"
        :href dl-url
        :aria-label "Download"}
    (heroicons/outline-folder-arrow-down)]])

(defn page-content [& {:keys [zoom-url srcset-urls]}]
  [:div
   [:template {:x-if "zoom"}
    (zoom-view :zoom-url zoom-url)]
   [:img {:srcset (format "%s 1x, %s 2x, %s 3x"
                          (:1x srcset-urls)
                          (:2x srcset-urls)
                          (:3x srcset-urls))
          :class "preview"}]])

(defn render [& {:keys [dl-url media preview-url router srcset-urls zoom-url]}]
  (tap> (str "render/preview-url: " preview-url))

  (-> (page/page :content (page-content :preview-url preview-url
                                        :srcset-urls srcset-urls
                                        :zoom-url zoom-url)
                 :page-title (:media/title media)
                 :page-title-buttons (page-title-buttons :dl-url dl-url)
                 :router router
                 :wrapper-attrs {:x-data (json/js {:zoom false})})
      (html/render-html)))

(defn handler [& {:keys [context ::r/router] :as _request}]
  (let [{:keys [current-organization resource imgix-media-domain]} context
        srcset-opts {;;:h 2048
                      ;;:w 2048
                      ;; :fit "clip"
                     :fit "max"
                     :auto "compress,format"
                     :cs "srgb"}
        preview-url (uri/uri-str {:scheme "https"
                                  :host imgix-media-domain
                                  :path (str "/" (:media/s3-key resource))
                                  :query (uri/map->query-string {:fix "max"})})
        img-path (str "/" (:media/s3-key resource))
        srcset-urls {:1x (uri/uri-str {:scheme "https"
                                       :host imgix-media-domain
                                       :path img-path
                                       :query (uri/map->query-string (merge srcset-opts {:dpr 1}))})
                     :2x (uri/uri-str {:scheme "https"
                                       :host imgix-media-domain
                                       :path img-path
                                       :query (uri/map->query-string (merge srcset-opts {:dpr 2}))})
                     :3x (uri/uri-str {:scheme "https"
                                       :host imgix-media-domain
                                       :path img-path
                                       :query (uri/map->query-string (merge srcset-opts {:dpr 3}))})}
        dl-url (uri/uri-str {:scheme "https"
                             :host imgix-media-domain
                             :path (str "/" (:media/s3-key resource))
                             :query (uri/map->query-string {:dl (:media/title resource)})})]

    (render :dl-url dl-url
            :media resource
            :org current-organization
            :preview-url preview-url
            :router router
            :srcset-urls srcset-urls
            :zoom-url preview-url)))
