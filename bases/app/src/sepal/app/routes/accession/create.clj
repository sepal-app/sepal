(ns sepal.app.routes.accession.create
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as ui.page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values]}]
  (accession.form/form :action (z/url-for accession.routes/new)
                       :errors errors
                       :values values))

(defn footer-buttons []
  [[:button {:class "btn"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]
   [:button {:class "btn btn-primary"
             :x-on:click "$dispatch('accession-form:submit')"}
    "Save"]])

(defn render [& {:keys [errors values]}]
  (ui.page/page :content (page-content :errors errors
                                       :values values)
                :footer (ui.form/footer :buttons (footer-buttons))
                :page-title "Create Accession"))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [acc (accession.i/create! tx data)]
        (accession.activity/create! tx accession.activity/created created-by acc)
        acc))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:code [:string {:min 1}]]
   [:taxon-id [:int {:min 0}]]
   [:date-received [:maybe validation.i/date]]
   [:date-accessioned [:maybe validation.i/date]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          ;; Validation error - return 422 with OOB error elements
          (http/validation-errors (validation.i/humanize result))
          ;; Valid - save and redirect
          (let [saved (create! db (:user/id viewer) result)]
            (-> (http/hx-redirect accession.routes/detail {:id (:accession/id saved)})
                (flash/success "Accession created successfully")))))

      (render :values form-params))))
