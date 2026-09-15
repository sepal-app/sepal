(ns sepal.app.routes.accession.create
  (:require [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.app.codes :as codes]
            [sepal.app.datetime :as datetime]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as ui.page]
            [sepal.database.interface :as db.i]
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
  (db.i/with-transaction [tx db]
    (let [acc (accession.i/create! tx data)]
      (accession.activity/create! tx accession.activity/created created-by acc)
      acc)))

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
   [:intended-location-id {:decode/form parse-long} [:maybe :int]]
   [:date-received [:maybe validation.i/date]]
   [:date-accessioned [:maybe validation.i/date]]
   [:received-type {:decode/form validation.i/empty->nil} [:maybe accession.spec/received-type]]
   [:quantity-received {:decode/form parse-long} [:maybe accession.spec/quantity-received]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db timezone]} context
        config (codes/accession db)
        today (datetime/today timezone)]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
        ;; Create is a wall: new data stays clean, and there is no override.
        (if (codes/rejects? config (:code data))
          (http/validation-errors
            (codes/shape-error (accession.i/next-code db (:template config) today)))
          (f/attempt-all [saved (f/try* (create! db (:user/id viewer) data))]
            (-> (http/hx-redirect accession.routes/detail {:id (:accession/id saved)})
                (flash/success "Accession created successfully"))
            (f/when-failed [e]
              (if (codes/unique-violation? e)
                (let [suggestion (accession.i/next-code db (:template config) today)]
                  (codes/taken-response
                    (:code data)
                    suggestion
                    #(accession.form/code-input :value suggestion
                                                :errors %
                                                :help accession.form/code-help)))
                (http/failure-flash e (http/hx-redirect accession.routes/new)
                                    "Could not create the accession")))))
        (f/when-failed [e]
          (http/failure-flash e (http/hx-redirect accession.routes/new)
                              "Could not create the accession")))

      ;; The field arrives filled in. With strict off it is a prefill you can
      ;; select and overwrite.
      (render :values (merge {:code (accession.i/next-code db (:template config) today)}
                             form-params)))))
