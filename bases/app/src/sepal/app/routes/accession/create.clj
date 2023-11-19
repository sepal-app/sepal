(ns sepal.app.routes.accession.create
  (:require [reitit.core :as r]
            [sepal.accession.interface :as accession.i]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.ui.page :as page]
            [sepal.error.interface :as error.i]))

(defn page-content [& {:keys [errors values router org]}]
  (accession.form/form :action (url-for router :org/taxa-new {:org-id (:organization/id org)})
                   :errors errors
                   :org org
                   :router router
                   :values values))

(defn render [& {:keys [errors org router values ]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :values values)
                 :page-title "Create accession"
                 :router router)
      (html/render-html)))

(defn handler [{:keys [context params request-method ::r/router]}]
  (let [{:keys [db]} context
        org (:current-organization context)]
    (case request-method
      :post
      (let [data (-> params
                     ;; TODO: Use the rank
                     (assoc :rank :genus)
                     (assoc :organization-id (:organization/id org)))
            result (accession.i/create! db data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :accession/detail {:id (:accession/id result)})
          (-> (found router :org/taxa-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values params))))
