(ns sepal.app.routes.location.detail
  (:require [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.location.panel :as location.panel]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.location.interface.permission :as location.perm]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors location values footer]}]
  (pages.record/page
    :name (:location/name location)
    :footer footer
    :body (location.form/form :action (z/url-for location.routes/detail
                                                 {:id (:location/id location)})
                              :errors errors
                              :values values)))

(defn render [& {:keys [errors location values panel-data timezone]}]
  (page/page :content (pages.detail/page-content-with-panel
                        :content (page-content :footer (ui.form/footer :buttons (location.form/footer-buttons))
                                               :errors errors
                                               :location location
                                               :values values)
                        :panel-content (location.panel/panel-content
                                         :location (:location panel-data)
                                         :stats (:stats panel-data)
                                         :moved-out (:moved-out panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)
                                         :timezone timezone))
             :breadcrumbs [[:a {:href (z/url-for location.routes/index)}
                            "Locations"]
                           (:location/name location)]))

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

(defn render-panel-page
  "Render the panel view as a full page for read-only users."
  [& {:keys [location panel-data timezone]}]
  (page/page
    :breadcrumbs [[:a {:href (z/url-for location.routes/index)} "Locations"]
                  (:location/name location)]
    :content [:div {:class "max-w-2xl mx-auto"}
              (location.panel/panel-content
                :location (:location panel-data)
                :stats (:stats panel-data)
                :moved-out (:moved-out panel-data)
                :activities (:activities panel-data)
                :activity-count (:activity-count panel-data)
                :timezone timezone)]))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:location/id resource)]
    ;; Readers see panel view as full page
    (if (not (authz/user-has-permission? viewer location.perm/edit))
      (let [panel-data (location.panel/fetch-panel-data db resource)]
        (render-panel-page :location resource :panel-data panel-data
                           :timezone timezone))
      ;; Editors/Admins see the form
      (let [values {:id id
                    :name (:location/name resource)
                    :code (:location/code resource)
                    :description (:location/description resource)}]
        (case request-method
          :post
          (let [result (validation.i/validate-form-values FormParams form-params)]
            (if (error.i/error? result)
              (http/validation-errors (validation.i/humanize result))
              (let [saved (update! db id (:user/id viewer) result)]
                (-> (http/hx-redirect location.routes/detail {:id (:location/id saved)})
                    (flash/success "Location updated successfully")))))

          (let [panel-data (location.panel/fetch-panel-data db resource)]
            (render :location resource
                    :values values
                    :panel-data panel-data
                    :timezone timezone)))))))
