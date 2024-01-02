(ns sepal.app.routes.taxon.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]))

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

(defn update! [db taxon-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [result (taxon.i/update! tx taxon-id data)]
      (when-not (error.i/error? result)
        (taxon.activity/create! tx taxon.activity/updated updated-by result))
      result)))

(defn handler [{:keys [context params request-method ::r/router viewer]}]
  (let [{:keys [db organization resource]} context
        parent (when (:taxon/parent-id resource)
                 (taxon.i/get-by-id db (:taxon/parent-id resource)))
        values (merge {:id (:taxon/id resource)
                       :name (:taxon/name resource)
                       :rank (:taxon/rank resource)
                       :parent-id (:taxon/id parent)
                       :parent-name (:taxon/name parent)}
                      params)]

    (case request-method
      :post
      (let [result (update! db (:taxon/id resource) (:user/id viewer) params)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found router :taxon/detail {:org-id (-> organization :organization/id str)
                                            :id (:taxon/id resource)})
          (-> (http/found router :taxon/detail)
              (assoc :flash {:error result
                             :values params}))))

      (render :org organization
              :router router
              :taxon resource
              :values values))))
