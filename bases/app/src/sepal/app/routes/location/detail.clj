(ns sepal.app.routes.location.detail
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.location.panel :as location.panel]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors location values]}]
  (location.form/form :action (z/url-for location.routes/detail {:id (:location/id location)})
                      :errors errors
                      :values values))

(defn render [& {:keys [errors location values panel-data]}]
  (page/page :content (pages.detail/page-content-with-panel
                        :content (page-content :errors errors
                                               :location location
                                               :values values)
                        :panel (location.panel/panel-content
                                 :location (:location panel-data)
                                 :stats (:stats panel-data)
                                 :activities (:activities panel-data)
                                 :activity-count (:activity-count panel-data)))
             :breadcrumbs [[:a {:href (z/url-for location.routes/index)}
                            "Locations"]
                           (:location/name location)]
             :footer (ui.form/footer :buttons (location.form/footer-buttons))))

(defn update! [db location-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [location (location.i/update! tx location-id data)]
        (location.activity/create! tx location.activity/updated updated-by location)
        location))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
        values {:id (:location/id resource)
                :name (:location/name resource)
                :code (:location/code resource)
                :description (:location/description resource)}]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (update! db (:location/id resource) (:user/id viewer) result)]
            (-> (http/hx-redirect location.routes/detail {:id (:location/id saved)})
                (flash/success "Location updated successfully")))))

      (let [panel-data (location.panel/fetch-panel-data db resource)]
        (render :location resource
                :values values
                :panel-data panel-data)))))
