(ns sepal.app.routes.taxon.create
  (:require [failjure.core :as f]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  [:div
   (taxon.form/form :action (z/url-for taxon.routes/new)
                    :errors errors
                    :values values)])

(defn render [& {:keys [errors flash values]}]
  (page/page :content (page-content :errors errors
                                    :values values)
             :flash flash
             :footer (form/footer :buttons (taxon.form/footer-buttons :on-cancel :back))
             :breadcrumbs [[:a {:href (z/url-for taxon.routes/index)} "Taxa"]
                           "New taxon"]))

(defn create! [db created-by data]
  (db.i/with-transaction [tx db]
    (let [taxon (taxon.i/create! tx data)]
      (taxon.activity/create! tx taxon.activity/created created-by taxon)
      taxon)))

(defn get-handler [{:keys [params flash]}]
  (let [{:keys [field-errors]} flash
        values (merge params (-> flash :values))]
    (render :errors field-errors
            :flash flash
            :values values)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db]} context]
    (f/attempt-all [data (validation.i/validate-form-values taxon.form/FormParams form-params)
                    saved (f/try* (create! db (:user/id viewer) data))]
      (http/hx-redirect (z/url-for taxon.routes/detail {:id (:taxon/id saved)}))
      (f/when-failed [e]
        (http/failure-flash e (http/hx-redirect taxon.routes/new) "Could not create the taxon")))))
