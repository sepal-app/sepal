(ns sepal.app.routes.material.detail.general
  (:require [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.app.codes :as codes]
            [sepal.app.datetime :as datetime]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.detail.shared :as material.shared]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.routes.material.panel :as material.panel]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors org material accession taxon values reasons footer separator]}]
  (material.shared/page
    :material material
    :accession accession
    :separator separator
    :taxon taxon
    :active material.shared/general-tab
    :footer footer
    :body (material.form/form :action (z/url-for material.routes/detail-general
                                                 {:id (:material/id material)})
                              :errors errors
                              :org org
                              :reasons reasons
                              :values values)))

(defn footer-buttons []
  (ui.form/footer-buttons :form-event "material-form" :on-cancel :reload))

(defn render [& {:keys [errors org material accession taxon values reasons timezone panel-data separator]}]
  (page/page :page-title-buttons (material.shared/actions :material material)
             :content (pages.detail/page-content-with-panel
                        :content (page-content :footer (ui.form/footer :buttons (footer-buttons))
                                               :errors errors
                                               :org org
                                               :material material
                                               :accession accession
                                               :values values
                                               :reasons reasons
                                               :separator separator
                                               :taxon taxon)
                        :panel-content (material.panel/panel-content
                                         :panel-data panel-data
                                         :material (:material panel-data)
                                         :accession (:accession panel-data)
                                         :taxon (:taxon panel-data)
                                         :location (:location panel-data)
                                         :history (:history panel-data)
                                         :observations (:observations panel-data)
                                         :observation-count (:observation-count panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)
                                         :timezone timezone))
             :breadcrumbs (material.shared/breadcrumbs :accession accession
                                                       :material material
                                                       :separator separator
                                                       :taxon taxon)))

(defn save! [db material-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [material (material.i/update! tx material-id data)]
      (material.activity/create! tx material.activity/updated updated-by material)
      material)))

(def FormParams
  [:map {:closed true}
   [:code [:string {:min 1}]]
   ;; Posted only by the confirmation tickbox a strict mismatch swaps in.
   [:code-override {:optional true} [:maybe :string]]
   [:accession-id [:int {:min 1}]]
   [:location-id [:maybe :int]]
   [:quantity [:int {:min 0}]]
   [:status [:string {:min 1}]]
   [:type [:string {:min 1}]]
   [:reason [:string {:min 0}]]])

(defn- page
  "The tab for `material`, as its GET renders it."
  [{:keys [db material-separator organization timezone]} material]
  (let [accession (accession.i/get-by-id db (:material/accession-id material))
        taxon (taxon.i/get-by-id db (:accession/taxon-id accession))
        location (location.i/get-by-id db (:material/location-id material))]
    (render :org organization
            :material material
            :accession accession
            :taxon taxon
            :values {:id (:material/id material)
                     :code (:material/code material)
                     :accession-id (:accession/id accession)
                     :accession-code (:accession/code accession)
                     :location-id (:material/location-id material)
                     :location-name (:location/name location)
                     :location-code (:location/code location)
                     :status (:material/status material)
                     :quantity (:material/quantity material)
                     :type (:material/type material)}
            :reasons (material.i/list-reasons db)
            :separator material-separator
            :timezone timezone
            :panel-data (material.panel/fetch-panel-data db material))))

(defn- confirm-code
  "A step: a halt asking to confirm the code when the template rejects it.
  Skipped when the code is untouched, as on the accession."
  [db config material data today]
  (when (and (codes/rejects? config (:code data))
             (not= (:code data) (:material/code material))
             (not= "1" (:code-override data)))
    (http/halt-with
      (http/unprocessable-entity
        (codes/confirm-swap (material.i/next-code db (:template config)
                                                  (:material/accession-id material)
                                                  today))))))

(defn get-handler [{:keys [::z/context]}]
  (page context (:resource context)))

(defn post-handler [{:keys [::z/context form-params viewer]}]
  (let [{:keys [db resource timezone]} context
        id (:material/id resource)]
    (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                    _confirmed (confirm-code db (codes/material db) resource data
                                             (datetime/today timezone))
                    _saved (f/try* (save! db id (:user/id viewer) data))]
      (http/saved (page context (material.i/get-by-id db id))
                  (tr "Material updated successfully"))
      (f/when-failed [e]
        (http/not-saved e (tr "Could not save the material"))))))
