(ns sepal.app.routes.location.delete
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(def resource-type :location)

(defn- label [location]
  (str "location " (:location/name location)))

(defn- render-dialog [db location]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for location.routes/delete {:id (:location/id location)})
      :label (label location)
      :blockers (app.delete/blockers resource-type db location)
      :archive-url (z/url-for location.routes/archive
                              {:id (:location/id location)}))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (app.delete/delete! resource-type db resource (:user/id viewer))]
        (if (error.i/error? result)
          (assoc (render-dialog db resource) :status 422)
          (-> (http/see-other location.routes/index)
              (flash/success (str "Deleted " (label resource))))))

      (render-dialog db resource))))
