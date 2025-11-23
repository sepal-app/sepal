(ns sepal.app.routes.location.detail
  (:require [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.location.form :as location.form]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [zodiac.core :as z]))

(defn page-content [& {:keys [errors location values]}]
  (location.form/form :action (z/url-for location.routes/detail {:id (:location/id location)})
                      :errors errors
                      :values values))

(defn render [& {:keys [errors location values]}]
  (page/page :content (page-content :errors errors
                                    :location location
                                    :values values)
             :breadcrumbs [[:a {:href (z/url-for location.routes/index)}
                            "Locations"]
                           (:location/name location)]
             :footer (ui.form/footer :buttons (location.form/footer-buttons))))

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

(defn handler [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource]} context
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
          (http/found location.routes/detail {:id (:location/id resource)})
          (-> (http/found location.routes/detail)
              (assoc :flash {:error error
                             :values values}))))

      (render :location resource
              :values values))))
