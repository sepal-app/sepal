(ns sepal.app.routes.location.create
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (location.form/form :action (z/url-for location.routes/new)
                      :errors errors
                      :values values))

(defn render [& {:keys [errors values]}]
  (page/page :content (page-content :errors errors
                                    :values values)
             :footer (ui.form/footer :buttons (location.form/footer-buttons))
             :page-title "Create Location"))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [location (location.i/create! tx data)]
        (location.activity/create! tx location.activity/created created-by location)
        location))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (create! db (:user/id viewer) result)]
            (-> (http/hx-redirect location.routes/detail {:id (:location/id saved)})
                (flash/success "Location created successfully")))))

      (render :values form-params))))
