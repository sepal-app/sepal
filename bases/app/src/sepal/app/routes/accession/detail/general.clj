(ns sepal.app.routes.accession.detail.general
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.detail.shared :as accession.shared]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.contact.interface :as contact.i]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors org accession supplier taxon values]}]
  [:div {:class "flex flex-col gap-2"}
   (accession.shared/tabs accession accession.shared/general-tab)
   (accession.form/form :action (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
                        :errors errors
                        :supplier supplier
                        :taxon taxon
                        :org org
                        :values values)])

(defn footer-buttons []
  [[:button {:class "btn"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]
   [:button {:class "btn btn-primary"
             :x-on:click "$dispatch('accession-form:submit')"}
    "Save"]])

(defn render [& {:keys [errors org accession supplier taxon values panel-data]}]
  (page/page :content (pages.detail/page-content-with-panel
                        :content (page-content :errors errors
                                               :org org
                                               :accession accession
                                               :supplier supplier
                                               :taxon taxon
                                               :values values)
                        :panel-content (accession.panel/panel-content
                                         :accession (:accession panel-data)
                                         :taxon (:taxon panel-data)
                                         :supplier (:supplier panel-data)
                                         :stats (:stats panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)))
             :breadcrumbs (accession.shared/breadcrumbs taxon accession)
             :footer (ui.form/footer :buttons (footer-buttons))))

(defn save! [db accession-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [accession (accession.i/update! tx accession-id data)]
        (accession.activity/create! tx accession.activity/updated updated-by accession)
        accession))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:code [:string {:min 1}]]
   [:taxon-id [:int {:min 0}]]
   ;; [:private {:decode/form validation.i/empty->nil} [:maybe accession.spec/private]]
   [:id-qualifier {:decode/form validation.i/empty->nil} [:maybe accession.spec/id-qualifier]]
   [:id-qualifier-rank {:decode/form validation.i/empty->nil} [:maybe accession.spec/id-qualifier-rank]]
   [:provenance-type {:decode/form validation.i/empty->nil} [:maybe accession.spec/provenance-type]]
   [:wild-provenance-status {:decode/form validation.i/empty->nil} [:maybe accession.spec/wild-provenance-status]]
   [:supplier-contact-id {:decode/form parse-long} [:maybe :int]]
   [:date-received [:maybe validation.i/date]]
   [:date-accessioned [:maybe validation.i/date]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db organization resource]} context
        taxon (taxon.i/get-by-id db (:accession/taxon-id resource))
        supplier (contact.i/get-by-id db (:accession/supplier-contact-id resource))
        values {:id (:accession/id resource)
                :code (:accession/code resource)
                :taxon-id (:accession/taxon-id resource)
                :taxon-name (:taxon/name taxon)
                :supplier-contact-id (:accession/supplier-contact-id resource)
                :id-qualifier (:accession/id-qualifier resource)
                :id-qualifier-rank (:accession/id-qualifier-rank resource)
                :provenance-type (:accession/provenance-type resource)
                :wild-provenance-status (:accession/wild-provenance-status resource)
                :date-received (:accession/date-received resource)
                :date-accessioned (:accession/date-accessioned resource)}]

    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (save! db (:accession/id resource) (:user/id viewer) result)]
            (if-not (error.i/error? saved)
              (http/hx-redirect (z/url-for accession.routes/detail {:id (:accession/id resource)}))
              (http/validation-errors (validation.i/humanize saved))))))

      (let [panel-data (accession.panel/fetch-panel-data db resource)]
        (render :org organization
                :accession resource
                :supplier supplier
                :taxon taxon
                :values values
                :panel-data panel-data)))))
