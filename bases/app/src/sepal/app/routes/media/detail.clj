(ns sepal.app.routes.media.detail
  (:require [lambdaisland.uri :as uri]
            [reitit.core :as r]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as page]
            [sepal.aws-s3.interface :as s3.i]
            [sepal.error.interface :as error.i]
            [sepal.media.interface :as media.i]))

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

(defn page-title-buttons [& {:keys [delete-url dl-url]}]
  [:<>
   [:button {:class "btn"
             :aria-label "Zoom"
             :x-on:click "zoom=true;"}
    (heroicons/magnifying-glass)]
   [:a {:class "btn"
        :href dl-url
        :aria-label "Download"}
    (heroicons/outline-folder-arrow-down)]
   [:a {:class "btn"
        :hx-delete delete-url
        :hx-confirm "Are you sure you want to delete this media?"
        :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
        :aria-label "Delete"}
    (heroicons/outline-trash :class "text-error")]])

(defn page-content [& {:keys [media router srcset-urls zoom-url]}]
  [:<>
   [:div
    [:template {:x-if "zoom"}
     (zoom-view :zoom-url zoom-url)]

    [:div#media-link-container
     {:hx-trigger "load"
      :hx-get (url-for router :media/detail.link {:id (:media/id media)})}]

    [:img {:srcset (format "%s 1x, %s 2x, %s 3x"
                           (:1x srcset-urls)
                           (:2x srcset-urls)
                           (:3x srcset-urls))
           :class "preview"}]]
   [:script {:type "module"
             :src (html/static-url "js/media_detail.ts")}]])

(defn render [& {:keys [dl-url media preview-url router srcset-urls zoom-url]}]
  (-> (page/page :content (page-content :media media
                                        :preview-url preview-url
                                        :router router
                                        :srcset-urls srcset-urls
                                        :zoom-url zoom-url)
                 :page-title (:media/title media)
                 :page-title-buttons (page-title-buttons :delete-url (url-for router
                                                                              :media/detail
                                                                              {:id (:media/id media)})
                                                         :dl-url dl-url)
                 :router router
                 ;; We have to put the x-data here b/c the zoom var is needed by
                 ;; the zoom button in the page-title-buttons
                 :wrapper-attrs {:x-data (json/js {:zoom false})})
      (html/render-html)))

(defn handler [& {:keys [context request-method ::r/router] :as _request}]
  (let [{:keys [db organization resource imgix-media-domain s3-client]} context
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

    (case request-method
      :delete
      (let [_ (media.i/delete! db (:media/id resource))
            _ (try
                (s3.i/delete-object s3-client (:media/s3-bucket resource) (:media/s3-key resource))
                (catch Exception ex
                  (error.i/ex->error ex)))]
        ;; TODO: handle errors
        (-> {:status 204
             :headers {"HX-Redirect" (url-for router
                                              :org/media
                                              {:org-id (:organization/id organization)})}}
            (flash/add-message "Deleted media.")))
      (render :dl-url dl-url
              :media resource
              :preview-url preview-url
              :router router
              :srcset-urls srcset-urls
              :zoom-url preview-url))))
