(ns sepal.app.routes.accession.detail.general
  (:require [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.app.codes :as codes]
            [sepal.app.datetime :as datetime]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.detail.shared :as accession.shared]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.collection.interface :as coll.i]
            [sepal.contact.interface :as contact.i]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.location.interface :as location.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors org accession location supplier taxon values
                              collection-available? footer today]}]
  (accession.shared/page
    :accession accession
    :taxon taxon
    :active accession.shared/general-tab
    :collection-available? collection-available?
    :footer footer
    :body (accession.form/form :action (z/url-for accession.routes/detail-general
                                                  {:id (:accession/id accession)})
                               :errors errors
                               :location location
                               :supplier supplier
                               :taxon taxon
                               :org org
                               :today today
                               :values values)))

(defn footer-buttons []
  (ui.form/footer-buttons :form-event "accession-form" :on-cancel :reload))

(defn render [& {:keys [errors org accession location supplier taxon values panel-data
                        timezone collection-available?]}]
  (page/page :page-title-buttons (accession.shared/actions :accession accession)
             :content (pages.detail/page-content-with-panel
                        :content (page-content :collection-available? collection-available?
                                               :footer (ui.form/footer :buttons (footer-buttons))
                                               :errors errors
                                               :org org
                                               :accession accession
                                               :location location
                                               :supplier supplier
                                               :taxon taxon
                                               :today (str (datetime/today timezone))
                                               :values values)
                        :panel-content (accession.panel/panel-content
                                         :panel-data panel-data
                                         :accession (:accession panel-data)
                                         :taxon (:taxon panel-data)
                                         :supplier (:supplier panel-data)
                                         :intended-location (:intended-location panel-data)
                                         :stats (:stats panel-data)
                                         :notes (:notes panel-data)
                                         :note-count (:note-count panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)
                                         :timezone timezone))
             :breadcrumbs (accession.shared/breadcrumbs taxon accession)))

(defn save! [db accession-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [accession (accession.i/update! tx accession-id data)]
      (accession.activity/create! tx accession.activity/updated updated-by accession)
      accession)))

(def FormParams
  [:map {:closed true}
   [:code [:string {:min 1}]]
   ;; Posted only by the confirmation tickbox a strict mismatch swaps in.
   [:code-override {:optional true} [:maybe :string]]
   [:taxon-id [:int {:min 0}]]
   ;; [:private {:decode/form validation.i/empty->nil} [:maybe accession.spec/private]]
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

(defn- page
  "The tab for `accession`, as its GET renders it."
  [{:keys [db organization timezone]} accession]
  (let [taxon (taxon.i/get-by-id db (:accession/taxon-id accession))
        supplier (contact.i/get-by-id db (:accession/supplier-contact-id accession))
        intended-location (location.i/get-by-id db (:accession/intended-location-id accession))
        collection (coll.i/get-by-accession-id db (:accession/id accession))]
    (render :collection-available? (accession.shared/collection-available?
                                     accession (some? collection))
            :org organization
            :accession accession
            :location intended-location
            :supplier supplier
            :taxon taxon
            :values {:id (:accession/id accession)
                     :code (:accession/code accession)
                     :taxon-id (:accession/taxon-id accession)
                     :taxon-name (:taxon/name taxon)
                     :supplier-contact-id (:accession/supplier-contact-id accession)
                     :intended-location-id (:accession/intended-location-id accession)
                     :id-qualifier (:accession/id-qualifier accession)
                     :id-qualifier-rank (:accession/id-qualifier-rank accession)
                     :provenance-type (:accession/provenance-type accession)
                     :wild-provenance-status (:accession/wild-provenance-status accession)
                     :date-received (:accession/date-received accession)
                     :date-accessioned (:accession/date-accessioned accession)
                     :received-type (:accession/received-type accession)
                     :quantity-received (:accession/quantity-received accession)}
            :panel-data (accession.panel/fetch-panel-data db accession)
            :timezone timezone)))

(defn- confirm-code
  "A step: a halt asking to confirm the code when the template rejects it.
  Skipped when the code is untouched. Moving an accession to a new location
  must not make you confirm a code you never edited, or every save on every
  legacy record grows a step."
  [db config accession data today]
  (when (and (codes/rejects? config (:code data))
             (not= (:code data) (:accession/code accession))
             (not= "1" (:code-override data)))
    (http/halt-with
      (http/unprocessable-entity
        (codes/confirm-swap (accession.i/next-code db (:template config) today))))))

(defn get-handler [{:keys [::z/context]}]
  (page context (:resource context)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:accession/id resource)
        today (datetime/today timezone)]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _dated (http/field-errors (validation.i/future-date-errors
                                                data [:date-received :date-accessioned]
                                                (str today)))
                    _confirmed (confirm-code db (codes/accession db) resource data today)
                    _saved (f/try* (save! db id (:user/id viewer) data))]
      (http/saved (page context (accession.i/get-by-id db id))
                  (tr "Accession updated successfully"))
      (f/when-failed [e]
        (http/not-saved e (tr "Could not save the accession"))))))
