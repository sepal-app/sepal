(ns sepal.app.routes.media.detail.shared
  (:require [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [ring.util.codec :as codec]
            [sepal.app.routes.media.detail.link-widget :as link-widget]
            [sepal.app.routes.media.panel :as media.panel]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.i18n.interface :refer [tr trc]]
            [sepal.media.interface :as media.i]
            [zodiac.core :as z]))

(defn- transform-url [media-id params]
  (str (z/url-for media.routes/transform {:id media-id})
       "?" (uri/map->query-string params)))

(defn- file-name [media]
  (some-> (:media/s3-key media) (str/split #"/") last))

(defn- display-name [media]
  (or (not-empty (:media/title media)) (file-name media) (tr "Untitled")))

(defn- extension [s]
  (some->> s (re-find #"\.([A-Za-z0-9]+)$") second))

(defn download-name
  "The title, with the original's extension added when the title has none. A
  title starts as the uploaded file's name, but an edited one usually drops the
  extension, and a download without one may not open."
  [media]
  (let [name (display-name media)
        ext (extension (:media/s3-key media))]
    (if (and ext (not= (some-> (extension name) str/lower-case) (str/lower-case ext)))
      (str name "." ext)
      name)))

(defn- download-url [media]
  (str (z/url-for media.routes/transform {:id (:media/id media)})
       "?dl=" (codec/url-encode (download-name media))))

(defn- zoom-dialog [media]
  ;; loading=lazy: the full-size image is fetched when the dialog opens, not
  ;; with the page.
  [:dialog#media-zoom {:class "spl-modal spl-modal--media"}
   [:div {:class "spl-modal-box"}
    [:form {:method "dialog" :class "flex justify-end mb-2"}
     [:button {:class "spl-btn spl-btn--sm"} (tr "Close")]]
    [:img {:src (transform-url (:media/id media) {:w 2048 :h 2048 :fit "contain"})
           :loading "lazy"
           :alt (display-name media)}]]
   [:form {:method "dialog" :class "spl-modal-backdrop"}
    [:button (tr "Close")]]])

(defn- image-stage [media]
  (let [id (:media/id media)
        size (fn [w h] (transform-url id {:w w :h h :fit "contain" :q 85}))]
    [:div {:class "spl-media-stage"}
     [:img {:srcset (format "%s 1x, %s 2x, %s 3x"
                            (size 800 600) (size 1600 1200) (size 2400 1800))
            :src (size 800 600)
            :alt (display-name media)
            :role "button"
            :tabindex "0"
            :aria-label (tr "Zoom")
            :onclick "document.getElementById('media-zoom').showModal()"
            :onkeydown "if (event.key === 'Enter') document.getElementById('media-zoom').showModal()"}]]))

(defn- edit-form [media]
  (ui.form/form
    (merge page/region-swap
           {:id "media-form"
            :hx-post (z/url-for media.routes/detail {:id (:media/id media)})
            :x-on:media-form:submit.window "$el.requestSubmit()"
            :x-on:media-form:reset.window "$el.reset()"})
    [(ui.form/anti-forgery-field)
     [:div {:class "spl-form mt-6"}
      (ui.form/section
        :title (tr "Details")
        :hint (tr "What this image shows and where it was taken.")
        :children
        [(ui.form/input-field :label (tr "Title")
                              :name "title"
                              :value (:media/title media))
         (ui.form/textarea-field :label (tr "Description")
                                 :name "description"
                                 :value (:media/description media))])]]))

(defn- body [media editor?]
  (list
    (zoom-dialog media)
    (image-stage media)
    (if editor?
      (edit-form media)
      (when-let [description (not-empty (:media/description media))]
        [:p {:class "mt-6 whitespace-pre-line"} description]))))

(defn- actions [media editor?]
  (let [download [:a {:class "spl-btn spl-btn--sm" :href (download-url media)}
                  (tr "Download")]]
    (if editor?
      (ui.actions/menu :primary download
                       :delete-url (z/url-for media.routes/delete {:id (:media/id media)}))
      [:div {:class "spl-actions"} download])))

(defn- render [& {:keys [media link panel-data editor? timezone]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for media.routes/index)} (trc "navigation" "Media")]
                  (display-name media)]
    :page-title-buttons (actions media editor?)
    :content (pages.detail/page-content-with-panel
               :content (pages.record/page
                          :name (display-name media)
                          :body (body media editor?)
                          :footer (when editor?
                                    (ui.form/footer
                                      :buttons (ui.form/footer-buttons :form-event "media-form"
                                                                       :on-cancel :reload))))
               :panel-content (media.panel/panel-content
                                :media media
                                :link-info (:link-info panel-data)
                                ;; An editor gets the link widget in place of the
                                ;; plain link. It is rendered here rather than
                                ;; loaded on its own: a morph never fires `load`
                                ;; again, so a lazy widget would empty itself on
                                ;; every save.
                                :linked (when editor?
                                          (link-widget/widget :link link
                                                              :link-info (:link-info panel-data)
                                                              :media media))
                                :uploader (:uploader panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)
                                :timezone timezone))))

(defn page
  "The media page for `media`, as its GET renders it."
  [{:keys [db material-separator timezone]} media editor?]
  (render :media media
          :link (media.i/get-link db (:media/id media))
          :editor? editor?
          :panel-data (media.panel/fetch-panel-data db media material-separator)
          :timezone timezone))
