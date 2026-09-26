(ns sepal.app.routes.contact.delete
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr]]
            [zodiac.core :as z]))

(def resource-type :contact)

(defn- render-dialog [db contact]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for contact.routes/delete {:id (:contact/id contact)})
      :title (tr "Delete contact %1?" (:contact/name contact))
      :blockers (app.delete/blockers resource-type db contact))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (app.delete/delete! resource-type db resource (:user/id viewer))]
        (if (error.i/error? result)
          (assoc (render-dialog db resource) :status 422)
          (-> (http/see-other contact.routes/index)
              (flash/success (tr "Deleted contact %1" (:contact/name resource))))))

      (render-dialog db resource))))
