(ns sepal.app.routes.material.create
  (:require [reitit.core :as r]
            [sepal.material.interface :as material.i]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.ui.page :as page]
            [sepal.error.interface :as error.i]))

(defn page-content [& {:keys [errors values router org]}]
  (material.form/form :action (url-for router :org/materials-new {:org-id (:organization/id org)})
                      :errors errors
                      :org org
                      :router router
                      :values values))

(defn render [& {:keys [errors org router values]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :values values)
                 :page-title "Create material"
                 :router router)
      (html/render-html)))

(defn handler [{:keys [context params request-method ::r/router]}]
  (let [{:keys [db]} context
        org (:current-organization context)]
    (case request-method
      :post
      (let [data (-> params
                     (assoc :organization-id (:organization/id org)))
            result (material.i/create! db data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :material/detail {:id (:material/id result)})
          (-> (found router :org/materials-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values params))))
