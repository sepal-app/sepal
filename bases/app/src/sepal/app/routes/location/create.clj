(ns sepal.app.routes.location.create
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]))

(defn page-content [& {:keys [errors values router org]}]
  (location.form/form :action (url-for router
                                       org.routes/locations-new
                                       {:org-id (:organization/id org)})
                      :errors errors
                      :org org
                      :router router
                      :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$refs.locationForm.submit()"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn render [& {:keys [errors org router values]}]
  (-> (page/page :attrs {:x-data "locationFormData"}
                 :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :values values)
                 :footer (ui.form/footer :buttons (footer-buttons))
                 :page-title "Create location"
                 :router router)
      (html/render-html)))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [location (location.i/create! tx data)]
        (location.activity/create! tx location.activity/created created-by location)
        location))
    (catch Exception ex
      (error.i/ex->error ex))))

(defn handler [{:keys [context params request-method ::r/router viewer]}]
  (let [{:keys [db]} context
        org (:organization context)]
    (case request-method
      :post
      (let [data (-> params
                     (assoc :organization-id (:organization/id org)))
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :location/detail {:id (:location/id result)})
          (-> (found router org.routes/locations-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values params))))
