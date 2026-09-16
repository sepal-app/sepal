(ns sepal.app.routes.location.create
  (:require [failjure.core :as f]
            [sepal.app.codes :as codes]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (location.form/form :action (z/url-for location.routes/new)
                      :errors errors
                      :values values))

(defn render [& {:keys [errors values]}]
  ;; Breadcrumbs rather than a page title: the top bar already answers "where
  ;; am I", and a heading repeating it pushed the first field down the page.
  (page/page :content (page-content :errors errors
                                    :values values)
             :footer (ui.form/footer :buttons (location.form/footer-buttons))
             :breadcrumbs [[:a {:href (z/url-for location.routes/index)} "Locations"]
                           "New location"]))

(defn create! [db created-by data]
  (db.i/with-transaction [tx db]
    (let [location (location.i/create! tx data)]
      (location.activity/create! tx location.activity/created created-by location)
      location)))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:code {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
        (f/attempt-all [saved (f/try* (create! db (:user/id viewer) data))]
          (-> (http/hx-redirect location.routes/detail {:id (:location/id saved)})
              (flash/success "Location created successfully"))
          (f/when-failed [e]
            ;; A code already in the garden is the one failure the form can
            ;; answer for itself. Saying so on the field beats a flash on a
            ;; reloaded page, which is what a second location with the same
            ;; code used to cost -- back when nothing refused it at all.
            (if (codes/unique-violation? e)
              (codes/taken-response (:code data)
                                    #(location.form/code-input :value (:code data)
                                                               :errors %))
              (http/failure-flash e (http/hx-redirect location.routes/new)
                                  "Could not create the location"))))
        (f/when-failed [e]
          (http/failure-flash e (http/hx-redirect location.routes/new) "Could not create the location")))

      (render :values form-params))))
