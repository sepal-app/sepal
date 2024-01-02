(ns sepal.app.routes.accession.detail
  (:require [reitit.core :as r]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
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
                                        :values values
                                        :taxon taxon)
                 :page-title (str (:accession/code accession) " - " (:taxon/name taxon))
                 :router router)
      (html/render-html)))

(defn update! [db accession-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [result (accession.i/update! tx accession-id data)]
      (when-not (error.i/error? result)
        (accession.activity/create! tx accession.activity/updated updated-by result))
      result)))

(defn handler [{:keys [context params request-method ::r/router viewer]}]
  (let [{:keys [db organization resource]} context
        taxon (taxon.i/get-by-id db (:accession/taxon-id resource))
        values (merge {:id (:accession/id resource)
                       :code (:accession/code resource)
                       :taxon-id (:accession/taxon-id resource)
                       :taxon-name (:taxon/name taxon)}
                      params)]

    (case request-method
      :post
      (let [result (update! db (:accession/id resource) (:user/id viewer) params)]
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
