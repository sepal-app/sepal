(ns sepal.app.routes.material.detail.general
  (:require [reitit.core :as r]
            [sepal.accession.interface :as accession.i]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.tabs :as tabs]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.taxon.interface :as taxon.i]))

(defn tab-items [& {:keys [router material]}]
  [{:label "General"
    :key :name
    :href (url-for router :material/detail-general {:id (:material/id material)})}
   {:label "Media"
    :key :media
    :href (url-for router :material/detail-media {:id (:material/id material)})}])

(defn page-content [& {:keys [errors org router material values]}]
  [:div {:class "flex flex-col gap-2"}
   (tabs/tabs :active :name
              :items (tab-items :router router :material material))
   (material.form/form :action (url-for router :material/detail {:id (:material/id material)})
                       :errors errors
                       :org org
                       :router router
                       :values values)])

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$refs.materialForm.submit()"}
    "Save"]
   [:button {:class "btn btn-secondary"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]])

(defn render [& {:keys [errors org router material accession taxon values]}]
  (-> (page/page :attrs {:x-data "materialFormData"}
                 :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :material material
                                        :values values
                                        :taxon taxon)
                 :footer (ui.form/footer :buttons (footer-buttons))
                 :page-title (format "%s.%s - %s"
                                     (:accession/code accession)
                                     (:material/code material)
                                     (:taxon/name taxon))
                 :router router)
      (html/render-html)))

(defn save! [db material-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [material (material.i/update! tx material-id data)]
        (material.activity/create! tx material.activity/updated updated-by material)
        material))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn handler [{:keys [context params request-method ::r/router viewer]}]
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
                      params)]

    (case request-method
      :post
      (let [result (save! db (:accession/id resource) (:user/id viewer) params)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found router :material/detail {:org-id (-> organization :organization/id str)
                                               :id (:material/id resource)})
          (-> (http/found router :accession/detail)
              ;; TODO: The errors needs to be parsed here and return a message
              (assoc :flash {:error result
                             :values params}))))

      (render :org organization
              :router router
              :material resource
              :accession accession
              :taxon taxon
              :values values))))
