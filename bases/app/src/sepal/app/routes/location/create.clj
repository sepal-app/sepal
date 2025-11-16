(ns sepal.app.routes.location.create
  (:require [sepal.app.http-response :refer [found see-other]]
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

(defn page-content [& {:keys [errors values]}]
  (location.form/form :action (z/url-for location.routes/new)
                      :errors errors
                      :values values))

(defn render [& {:keys [errors values]}]
  (page/page :content (page-content :errors errors
                                    :values values)
             :footer (ui.form/footer :buttons (location.form/footer-buttons))
             :page-title "Create Location"))

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
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [data (params/decode FormParams form-params)
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other location.routes/detail {:id (:location/id result)})
          (-> (found location.routes/new)
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :values form-params))))
