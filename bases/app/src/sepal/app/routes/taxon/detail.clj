(ns sepal.app.routes.taxon.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.page :as page]
            [sepal.taxon.interface :as taxon.i]))

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

(defn handler [{:keys [context params request-method ::r/router]}]
  (let [{:keys [db organization resource]} context
        parent (when (:taxon/parent-id resource)
                 (taxon.i/get-by-id db (:taxon/parent-id resource)))
        error nil
        values (merge {:id (:taxon/id resource)
                       :name (:taxon/name resource)
                       :rank (:taxon/rank resource)
                       :parent-id (:taxon/id parent)
                       :parent-name (:taxon/name parent)}
                      params)]

    (case request-method
      :post
      (let [result (taxon.i/update! db (:taxon/id resource) params)]
        ;; TODO: handle errors
        (if-not error
          (http/found router :taxon/detail {:org-id (-> organization :organization/id str)
                                            :id (:taxon/id resource)})
          (-> (http/found router :taxon/detail)
              (assoc :flash {:error error
                             :values params}))))

      (render :org organization
              :router router
              :taxon resource
              :values values))))
