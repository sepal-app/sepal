(ns sepal.app.routes.material.detail
  (:require [reitit.core :as r]
            [sepal.accession.interface :as accession.i]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.ui.page :as page]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.taxon.interface :as taxon.i]))

(defn page-content [& {:keys [errors org router material values]}]
  (material.form/form :action (url-for router :material/detail {:id (:material/id material)})
                      :errors errors
                      :org org
                      :router router
                      :values values))

(defn render [& {:keys [errors org router material accession taxon values]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :material material
                                        :values values
                                        :taxon taxon)
                 :page-title (format "%s.%s - %s"
                                     (:accession/code accession)
                                     (:material/code material)
                                     (:taxon/name taxon))
                 :router router)
      (html/render-html)))

(defn handler [{:keys [context params request-method ::r/router]}]
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
                       :location-code (:location/code location)}
                      params)]

    (case request-method
      :post
      (let [result (material.i/update! db (:accession/id resource) params)]
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
