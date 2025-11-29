(ns sepal.app.routes.taxon.detail.name
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.detail.shared :as taxon.shared]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.alert :as alert]
            [sepal.app.ui.dropdown :as dropdown]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-title-buttons [& {:keys []}]
  (dropdown/dropdown "Actions"
                     (dropdown/item (z/url-for taxon.routes/new) "Add a taxon")))

(defn page-content [& {:keys [errors taxon values]}]
  [:div {:class "flex flex-col gap-2"}
   (taxon.shared/tabs taxon taxon.shared/name-tab)
   (let [read-only? (:taxon/read-only taxon)]
     [:div
      (when read-only?
        (alert/info "Taxa from the WFO Plantlist are not editable."))
      (taxon.form/form :action (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                       :errors errors
                       :read-only read-only?
                       :values values)])])

(defn render [& {:keys [errors taxon values]}]
  (page/page :content (page-content :errors errors
                                    :taxon taxon
                                    :values values)
             :breadcrumbs (taxon.shared/breadcrumbs taxon)
             :footer (ui.form/footer :buttons (taxon.form/footer-buttons))
             :page-title-buttons (page-title-buttons)))

(defn save! [db taxon-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [taxon (taxon.i/update! tx taxon-id data)]
        (taxon.activity/create! tx taxon.activity/updated updated-by taxon)
        taxon))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values taxon.form/FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (save! db (:taxon/id resource) (:user/id viewer) result)]
            (if-not (error.i/error? saved)
              (http/hx-redirect (z/url-for taxon.routes/detail {:id (:taxon/id saved)}))
              (http/validation-errors (validation.i/humanize saved))))))

      :get
      (let [parent (when (:taxon/parent-id resource)
                     (taxon.i/get-by-id db (:taxon/parent-id resource)))
            values {:id (:taxon/id resource)
                    :name (:taxon/name resource)
                    :rank (:taxon/rank resource)
                    :author (:taxon/author resource)
                    :parent-id (:taxon/id parent)
                    :parent-name (:taxon/name parent)
                    :vernacular-names (:taxon/vernacular-names resource)}]
        (render :taxon resource
                :values values)))))
