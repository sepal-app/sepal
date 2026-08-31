(ns sepal.app.routes.material.create
  (:require [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (material.form/form :action (z/url-for material.routes/new)
                      :errors errors
                      :values values))

(defn footer-buttons []
  ;; This one also had Save before Cancel, the opposite order from every other
  ;; form in the app.
  (ui.form/footer-buttons :form-event "material-form" :on-cancel :back))

(defn render [& {:keys [errors values]}]
  ;; Breadcrumbs rather than a page title: the top bar already answers "where
  ;; am I", and a heading repeating it pushed the first field down the page.
  (page/page :content (page-content :errors errors
                                    :values values)
             :footer (ui.form/footer :buttons (footer-buttons))
             :breadcrumbs [[:a {:href (z/url-for material.routes/index)} "Material"]
                           "New material"]))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [acc (material.i/create! tx data)]
        (material.activity/create! tx material.activity/created created-by acc)
        acc))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:code [:string {:min 1}]]
   [:accession-id [:int {:min 1}]]
   [:location-id [:maybe :int]]
   [:quantity [:int {:min 1}]]
   [:status [:string {:min 1}]]
   [:type [:string {:min 1}]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (create! db (:user/id viewer) result)]
            (-> (http/hx-redirect material.routes/detail {:id (:material/id saved)})
                (flash/success "Material created successfully")))))
      (render))))
