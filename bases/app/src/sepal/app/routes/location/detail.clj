(ns sepal.app.routes.location.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.ui.page :as page]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]))

(defn page-content [& {:keys [errors org router location values]}]
  (location.form/form :action (url-for router :location/detail {:id (:location/id location)})
                   :errors errors
                   :org org
                   :router router
                   :values values))

(defn render [& {:keys [errors org router location values]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :location location
                                        :values values)
                 :page-title (:location/name location)
                 :router router)
      (html/render-html)))

(defn handler [{:keys [context params request-method ::r/router]}]
  (let [{:keys [db organization resource]} context
        error nil
        values (merge {:id (:location/id resource)
                       :name (:location/name resource)
                       :code (:location/code resource)
                       :description (:location/description resource)}
                      params)]

    (case request-method
      :post
      (let [result (location.i/update! db (:location/id resource) params)]
        (tap> (str "result: " result))
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found router :location/detail {:org-id (-> organization :organization/id str)
                                               :id (:location/id resource)})
          (-> (http/found router :location/detail)
              (assoc :flash {:error error
                             :values params}))))

      (render :org organization
              :router router
              :location resource
              :values values))))
