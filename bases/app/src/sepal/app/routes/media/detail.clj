(ns sepal.app.routes.media.detail
  (:require [clojure.string :as str]
            [failjure.core :as f]
            [lambdaisland.uri :as uri]
            [ring.util.codec :as codec]
            [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.media.panel :as media.panel]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.database.interface :as db.i]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
            [sepal.media.interface.permission :as media.perm]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn- transform-url [media-id params]
  (str (z/url-for media.routes/transform {:id media-id})
       "?" (uri/map->query-string params)))

(defn- file-name [media]
  (some-> (:media/s3-key media) (str/split #"/") last))

(defn- display-name [media]
  (or (not-empty (:media/title media)) (file-name media) "Untitled"))

(defn- download-url [media]
  (str (z/url-for media.routes/transform {:id (:media/id media)})
       "?dl=" (codec/url-encode (display-name media))))

(defn- zoom-dialog [media]
  ;; loading=lazy: the full-size image is fetched when the dialog opens, not
  ;; with the page.
  [:dialog#media-zoom {:class "spl-modal spl-modal--media"}
   [:div {:class "spl-modal-box"}
    [:form {:method "dialog" :class "flex justify-end mb-2"}
     [:button {:class "spl-btn spl-btn--sm"} "Close"]]
    [:img {:src (transform-url (:media/id media) {:w 2048 :h 2048 :fit "contain"})
           :loading "lazy"
           :alt (display-name media)}]]
   [:form {:method "dialog" :class "spl-modal-backdrop"}
    [:button "Close"]]])

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
            :aria-label "Zoom"
            :onclick "document.getElementById('media-zoom').showModal()"
            :onkeydown "if (event.key === 'Enter') document.getElementById('media-zoom').showModal()"}]]))

(defn- caption [media]
  [:p {:class "mt-2 text-sm text-text-soft"}
   (->> [(file-name media)
         (:media/media-type media)
         (media.ui/format-size (:media/size-in-bytes media))]
        (remove nil?)
        (str/join " · "))])

(defn- edit-form [media]
  (ui.form/form
    {:id "media-form"
     :hx-post (z/url-for media.routes/detail {:id (:media/id media)})
     :hx-swap "none"
     :x-on:media-form:submit.window "$el.requestSubmit()"
     :x-on:media-form:reset.window "$el.reset()"}
    [:div {:class "spl-form mt-6"}
     (ui.form/anti-forgery-field)
     (ui.form/input-field :label "Title"
                          :name "title"
                          :value (:media/title media))
     (ui.form/textarea-field :label "Description"
                             :name "description"
                             :value (:media/description media))]))

(defn- body [media editor?]
  (list
    (zoom-dialog media)
    (image-stage media)
    (caption media)
    (if editor?
      (edit-form media)
      (when-let [description (not-empty (:media/description media))]
        [:p {:class "mt-6 whitespace-pre-line"} description]))))

(defn- actions [media editor?]
  (let [download [:a {:class "spl-btn spl-btn--sm" :href (download-url media)}
                  "Download"]]
    (if editor?
      (ui.actions/menu :primary download
                       :delete-url (z/url-for media.routes/delete {:id (:media/id media)}))
      [:div {:class "spl-actions"} download])))

(defn render [& {:keys [media panel-data editor? timezone]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for media.routes/index)} "Media"]
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
                                ;; plain link. It loads on its own, as it did above
                                ;; the image.
                                :linked (when editor?
                                          [:div {:hx-get (z/url-for media.routes/detail-link
                                                                    {:id (:media/id media)})
                                                 :hx-trigger "load"}])
                                :uploader (:uploader panel-data)
                                :activities (:activities panel-data)
                                :activity-count (:activity-count panel-data)
                                :timezone timezone))))

(def FormParams
  [:map {:closed true}
   [:title {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn save! [db media-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [media (media.i/update! tx media-id data)]
      (media.activity/create! tx media.activity/updated updated-by media)
      media)))

(defn handler [& {:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db material-separator resource timezone]} context
        detail-url (z/url-for media.routes/detail {:id (:media/id resource)})]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      _ (f/try* (save! db (:media/id resource) (:user/id viewer) data))]
        (-> (http/hx-redirect detail-url)
            (flash/success "Media updated successfully"))
        (f/when-failed [e]
          (http/failure-flash e (http/hx-redirect detail-url) "Could not save the media")))

      (render :media resource
              :editor? (authz/user-has-permission? viewer media.perm/edit)
              :panel-data (media.panel/fetch-panel-data db resource material-separator)
              :timezone timezone))))
