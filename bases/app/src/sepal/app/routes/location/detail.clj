(ns sepal.app.routes.location.detail
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]))

(defn page-content [& {:keys [errors org router location values]}]
  (location.form/form :action (url-for router :location/detail {:id (:location/id location)})
                      :errors errors
                      :org org
                      :router router
                      :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('location-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             ;; TODO: form.reset() would be better but it doesn't reset the TomSelect of the rank field
             ;; :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && $refs.taxonForm.reset()"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]])

(defn render [& {:keys [errors org router location values]}]
  (-> (page/page :attrs {:x-data "locationFormData"}
                 :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :location location
                                        :values values)
                 :footer (ui.form/footer :buttons (footer-buttons))
                 :page-title (:location/name location)
                 :router router)
      (html/render-html)))

(defn update! [db location-id updated-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [location (location.i/update! tx location-id data)]
        (location.activity/create! tx location.activity/updated updated-by location)
        location))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:name :string]
   [:code [:maybe :string]]
   [:description [:maybe :string]]])

(defn handler [{:keys [context form-params request-method ::r/router viewer]}]
  (let [{:keys [db organization resource]} context
        error nil
        values (merge {:id (:location/id resource)
                       :name (:location/name resource)
                       :code (:location/code resource)
                       :description (:location/description resource)}
                      (params/decode FormParams form-params))]

    (case request-method
      :post
      (let [result (update! db (:location/id resource) (:user/id viewer) values)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found router :location/detail {:org-id (-> organization :organization/id str)
                                               :id (:location/id resource)})
          (-> (http/found router :location/detail)
              (assoc :flash {:error error
                             :values values}))))

      (render :org organization
              :router router
              :location resource
              :values values))))
