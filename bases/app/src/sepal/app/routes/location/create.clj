(ns sepal.app.routes.location.create
  (:require [sepal.app.http-response :refer [found see-other]]
            [sepal.app.params :as params]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors values org]}]
  (location.form/form :action (z/url-for
                                org.routes/locations-new
                                {:org-id (:organization/id org)})
                      :errors errors
                      :org org
                      :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('location-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn render [& {:keys [errors org values]}]
  (page/page :attrs {:x-data "locationFormData"}
             :content (page-content :errors errors
                                    :org org
                                    :values values)
             :footer (ui.form/footer :buttons (footer-buttons))
             :page-title "Create location"))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [location (location.i/create! tx data)]
        (location.activity/create! tx location.activity/created created-by location)
        location))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:name :string]
   [:code [:maybe :string]]
   [:description [:maybe :string]]])

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db]} context
        org (:organization context)]
    (case request-method
      :post
      (let [data (-> (params/decode FormParams form-params)
                     (assoc :organization-id (:organization/id org)))
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other :location/detail {:id (:location/id result)})
          (-> (found org.routes/locations-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :values form-params))))
