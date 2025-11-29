(ns sepal.app.routes.material.detail.general
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.detail.shared :as material.shared]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors org material values]}]
  [:div {:class "flex flex-col gap-2"}
   (material.shared/tabs material material.shared/general-tab)
   (material.form/form :action (z/url-for material.routes/detail-general {:id (:material/id material)})
                       :errors errors
                       :org org
                       :values values)])

(defn footer-buttons []
  [[:button {:class "btn"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]
   [:button {:class "btn btn-primary"
             :x-on:click "$dispatch('material-form:submit')"}
    "Save"]])

(defn render [& {:keys [errors org material accession taxon values]}]
  (page/page :content (page-content :errors errors
                                    :org org
                                    :material material
                                    :values values
                                    :taxon taxon)
             :breadcrumbs (material.shared/breadcrumbs :accession accession
                                                       :material material
                                                       :taxon taxon)
             :footer (ui.form/footer :buttons (footer-buttons))))

(defn save! [db material-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [material (material.i/update! tx material-id data)]
        (material.activity/create! tx material.activity/updated updated-by material)
        material))
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
  (let [{:keys [db organization resource]} context
        accession (accession.i/get-by-id db (:material/accession-id resource))
        taxon (taxon.i/get-by-id db (:accession/taxon-id accession))
        location (location.i/get-by-id db (:material/location-id resource))
        values {:id (:material/id resource)
                :code (:material/code resource)
                :accession-id (:accession/id accession)
                :accession-code (:accession/code accession)
                :location-id (:material/location-id resource)
                :location-name (:location/name location)
                :location-code (:location/code location)
                :status (:material/status resource)
                :quantity (:material/quantity resource)
                :type (:material/type resource)}]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (save! db (:material/id resource) (:user/id viewer) result)]
            (-> (http/hx-redirect material.routes/detail {:id (:material/id saved)})
                (flash/success "Material updated successfully")))))

      (render :org organization
              :material resource
              :accession accession
              :taxon taxon
              :values values))))
