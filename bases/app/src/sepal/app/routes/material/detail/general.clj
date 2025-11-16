(ns sepal.app.routes.material.detail.general
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.detail.tabs :as material.tabs]
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
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors org material values]}]
  [:div {:class "flex flex-col gap-2"}
   (material.tabs/tabs material material.tabs/general-tab)
   (material.form/form :action (z/url-for material.routes/detail-general {:id (:material/id material)})
                       :errors errors
                       :org org
                       :values values)])

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('material-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]])

(defn render [& {:keys [errors org material accession taxon values]}]
  (page/page :content (page-content :errors errors
                                    :org org
                                    :material material
                                    :values values
                                    :taxon taxon)
             :footer (ui.form/footer :buttons (footer-buttons))
             :page-title (format "%s.%s - %s"
                                 (:accession/code accession)
                                 (:material/code material)
                                 (:taxon/name taxon))))

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
   [:code :string]
   [:accession-id :int]
   [:location-id [:maybe :int]]
   [:quantity :int]
   [:status :string]
   [:type :string]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db organization resource]} context
        accession (accession.i/get-by-id db (:material/accession-id resource))
        taxon (taxon.i/get-by-id db (:accession/taxon-id accession))
        location (location.i/get-by-id db (:material/location-id resource))
        values (merge {:id (:material/id resource)
                       :code (:material/code resource)
                       :accession-id (:accession/id accession)
                       :accession-code (:accession/code accession)
                       :location-id (:material/location-id resource)
                       :location-name (:location/name location)
                       :location-code (:location/code location)
                       :status (:material/status resource)
                       :quantity (:material/quantity resource)
                       :type (:material/type resource)}
                      (params/decode FormParams form-params))]
    (case request-method
      :post
      (let [result (save! db (:material/id resource) (:user/id viewer) values)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found material.routes/detail {:id (:material/id resource)})
          (-> (http/found accession.routes/detail)
              ;; TODO: The errors needs to be parsed here and return a message
              (assoc :flash {:error "Error saving material"  ;; TODO: result
                             :values values}))))

      (render :org organization
              :material resource
              :accession accession
              :taxon taxon
              :values values))))
