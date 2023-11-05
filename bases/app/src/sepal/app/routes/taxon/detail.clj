(ns sepal.app.routes.taxon.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.page :as page]
            [sepal.taxon.interface :as taxon.i]
            [sepal.organization.interface :as org.i]))

(defn page-content [& {:keys [errors org router taxon values]}]
  (taxon.form/form :action (url-for router :taxon/detail {:id (:taxon/id taxon)})
                   :errors errors
                   :org org
                   :router router
                   :values values))

(defn render [& {:keys [errors org router taxon values]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :taxon taxon
                                        :values values)
                 :page-title (:taxon/name taxon)
                 :router router)
      (html/render-html)))

(defn handler [{:keys [context params path-params request-method ::r/router viewer]}]
  (let [{:keys [db]} context
        ;; org (:organization session)
        ;; org (:current-organization context)
        taxon-id (-> path-params :id Integer/parseInt)
        ;; error (:error taxon)
        taxon (taxon.i/get-by-id db taxon-id)
        org (org.i/get-by-id db (:taxon/organization-id taxon))
        parent (when (:taxon/parent-id taxon)
                 (taxon.i/get-by-id db (:taxon/parent-id taxon)))
        error nil
        values (merge {:id (:taxon/id taxon)
                       :name (:taxon/name taxon)
                       :rank (:taxon/rank taxon)
                       :parent-id (:taxon/id parent)
                       :parent-name (:taxon/name parent)}
                      params)]

    (case request-method
      :post
      (let [taxon (taxon.i/update! db taxon-id params)]
        ;; TODO: handle errors
        (if-not error
          (http/found router :taxon/detail {:org-id (-> org :organization/id str)
                                            :id (:taxon/id taxon)})
          (-> (http/found router :taxon/detail)
              (assoc :flash {:error error
                             :values params}))))

      (-> (render :org org
                  :router router
                  :user viewer
                  :taxon taxon
                  :values values)))))
