(ns sepal.app.routes.accession.create
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
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
  (ui.form/footer-buttons :form-event "accession-form" :on-cancel :back))

(defn render [& {:keys [errors values]}]
  ;; Breadcrumbs rather than a page title: the top bar already answers "where
  ;; am I", and a heading repeating it pushed the first field down the page.
  (ui.page/page :content (page-content :errors errors
                                       :values values)
                :footer (ui.form/footer :buttons (footer-buttons))
                :breadcrumbs [[:a {:href (z/url-for accession.routes/index)}
                               "Accessions"]
                              "New accession"]))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [acc (accession.i/create! tx data)]
        (accession.activity/create! tx accession.activity/created created-by acc)
        acc))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  ;; Every field the form posts. The map is closed, so a key missing from here
  ;; is dropped in silence: creating an accession discarded its provenance, ID
  ;; qualifier and supplier, and you only got them by saving and then editing.
  ;; This matches `detail/general.clj`, which had them all along.
  [:map {:closed true}
   [:code [:string {:min 1}]]
   [:taxon-id [:int {:min 0}]]
   [:id-qualifier {:decode/form validation.i/empty->nil} [:maybe accession.spec/id-qualifier]]
   [:id-qualifier-rank {:decode/form validation.i/empty->nil} [:maybe accession.spec/id-qualifier-rank]]
   [:provenance-type {:decode/form validation.i/empty->nil} [:maybe accession.spec/provenance-type]]
   [:wild-provenance-status {:decode/form validation.i/empty->nil} [:maybe accession.spec/wild-provenance-status]]
   [:supplier-contact-id {:decode/form parse-long} [:maybe :int]]
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
