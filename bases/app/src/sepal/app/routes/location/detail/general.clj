(ns sepal.app.routes.location.detail.general
  (:require [failjure.core :as f]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.detail.shared :as location.shared]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.location.panel :as location.panel]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors location values footer]}]
  (location.shared/page
    :location location
    :active location.shared/general-tab
    :footer footer
    :body (location.form/form :action (z/url-for location.routes/detail-general
                                                 {:id (:location/id location)})
                              :errors errors
                              :values values)))

(defn render [& {:keys [errors location values panel-data timezone]}]
  (page/page :page-title-buttons (location.shared/actions :location location)
             :content (pages.detail/page-content-with-panel
                        :content (page-content :footer (ui.form/footer :buttons (location.form/footer-buttons))
                                               :errors errors
                                               :location location
                                               :values values)
                        :panel-content (location.panel/panel-content
                                         :panel-data panel-data
                                         :location (:location panel-data)
                                         :stats (:stats panel-data)
                                         :awaiting (:awaiting panel-data)
                                         :moved-out (:moved-out panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)
                                         :timezone timezone))
             :breadcrumbs (location.shared/breadcrumbs location (:ancestors panel-data))))

(defn update! [db location-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [location (location.i/update! tx location-id data)]
      (location.activity/create! tx location.activity/updated updated-by location)
      location)))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]
   ;; Always posted, so an empty one clears the parent.
   [:parent-id {:optional true :decode/form validation.i/empty->nil} [:maybe :int]]])

(defn- page
  "The tab for `location`, as its GET renders it."
  [{:keys [db timezone]} location]
  (let [parent (some->> (:location/parent-id location) (location.i/get-by-id db))]
    (render :location location
            :values {:id (:location/id location)
                     :name (:location/name location)
                     :code (:location/code location)
                     :description (:location/description location)
                     :parent-id (:location/id parent)
                     :parent-code (:location/code parent)
                     :parent-name (:location/name parent)}
            :panel-data (location.panel/fetch-panel-data db location)
            :timezone timezone)))

(defn get-handler [{:keys [::z/context]}]
  (page context (:resource context)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource]} context
        id (:location/id resource)]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _saved (f/try* (update! db id (:user/id viewer) data))]
      (http/saved (page context (location.i/get-by-id db id))
                  (tr "Location updated successfully"))
      (f/when-failed [e]
        (http/not-saved e (tr "Could not save the location"))))))
