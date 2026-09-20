(ns sepal.app.routes.location.detail.general
  (:require [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.detail.shared :as location.shared]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.location.panel :as location.panel]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
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
                                         :location (:location panel-data)
                                         :stats (:stats panel-data)
                                         :awaiting (:awaiting panel-data)
                                         :moved-out (:moved-out panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)
                                         :timezone timezone))
             :breadcrumbs (location.shared/breadcrumbs location)))

(defn update! [db location-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [location (location.i/update! tx location-id data)]
      (location.activity/create! tx location.activity/updated updated-by location)
      location)))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:location/id resource)
        values {:id id
                :name (:location/name resource)
                :code (:location/code resource)
                :description (:location/description resource)}]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                      saved (f/try* (update! db id (:user/id viewer) data))]
        (-> (http/hx-redirect location.routes/detail-general {:id (:location/id saved)})
            (flash/success "Location updated successfully"))
        (f/when-failed [e]
          (http/failure-flash e (http/hx-redirect location.routes/detail-general {:id id}) "Could not save the location")))

      (let [panel-data (location.panel/fetch-panel-data db resource)]
        (render :location resource
                :values values
                :panel-data panel-data
                :timezone timezone)))))
