(ns sepal.app.routes.accession.detail
  (:require [reitit.core :as r]
            [sepal.accession.interface :as accession.i]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.ui.page :as page]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]
            [sepal.taxon.interface :as taxon.i]))

(defn page-content [& {:keys [errors org router accession values]}]
  (accession.form/form :action (url-for router :accession/detail {:id (:accession/id accession)})
                   :errors errors
                   :org org
                   :router router
                   :values values))

(defn render [& {:keys [errors org router accession taxon values]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :accession accession
                                        :values values)
                 :page-title (str (:accession/code accession) " - " (:taxon/name taxon))
                 :router router)
      (html/render-html)))

(defn handler [{:keys [context params path-params request-method ::r/router]}]
  (let [{:keys [db organization resource]} context
        taxon (taxon.i/get-by-id db (:accession/taxon-id resource))
        values (merge {:id (:accession/id resource)
                       :code (:accession/code resource)
                       :taxon-id (:accession/taxon-id resource)
                       :taxon-name (:taxon/name taxon)}
                      params)]

    (case request-method
      :post
      (let [result (accession.i/update! db (:accession/id resource) params)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found router :accession/detail {:org-id (-> organization :organization/id str)
                                                :id (:accession/id resource)})
          (-> (http/found router :accession/detail)
              ;; TODO: The errors needs to be parsed here and return a message
              (assoc :flash {:error result
                             :values params}))))

      (render :org organization
              :router router
              :accession resource
              :taxon taxon
              :values values))))
