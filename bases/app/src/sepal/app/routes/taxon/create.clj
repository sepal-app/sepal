(ns sepal.app.routes.taxon.create
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
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
             :footer (form/footer :buttons (taxon.form/footer-buttons))
             :page-title "Create Taxon"))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [taxon (taxon.i/create! tx data)]
        (taxon.activity/create! tx taxon.activity/created created-by taxon)
        taxon))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn get-handler [{:keys [params flash]}]
  (let [{:keys [field-errors]} flash
        values (merge params (-> flash :values))]
    (render :errors field-errors
            :flash flash
            :values values)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db]} context
        result (validation.i/validate-form-values taxon.form/FormParams form-params)]
    (if (error.i/error? result)
      (http/validation-errors (validation.i/humanize result))
      (let [saved (create! db (:user/id viewer) result)]
        (if-not (error.i/error? saved)
          (http/hx-redirect (z/url-for taxon.routes/detail {:id (:taxon/id saved)}))
          (http/validation-errors (validation.i/humanize saved)))))))
