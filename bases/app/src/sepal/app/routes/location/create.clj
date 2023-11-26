(ns sepal.app.routes.location.create
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.ui.page :as page]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.location.form :as location.form]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]))


(defn page-content [& {:keys [errors values router org]}]
  (location.form/form :action (url-for router :org/location-new {:org-id (:organization/id org)})
                      :errors errors
                      :org org
                      :router router
                      :values values))

(defn render [& {:keys [errors org router values ]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :values values)
                 :page-title "Create location"
                 :router router)
      (html/render-html)))

(defn handler [{:keys [context params request-method ::r/router]}]
  (tap> "loc.create/handler")
  (let [{:keys [db]} context
        org (:current-organization context)]
    (case request-method
      :post
      (let [data (-> params
                     (assoc :organization-id (:organization/id org)))
            result (location.i/create! db data)]
        (tap> (str "result: " result))

        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :location/detail {:id (:location/id result)})
          (-> (found router :org/location-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values params))))
