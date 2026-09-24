(ns sepal.app.routes.propagation.delete
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.propagation.shared :as shared]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(def resource-type :propagation)

(defn- label [db propagation]
  (str "propagation from "
       (shared/parent-name (accession.i/get-by-id
                             db (:propagation/parent-accession-id propagation))
                           nil
                           nil)))

(defn- render-dialog [db propagation]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for propagation.routes/delete {:id (:propagation/id propagation)})
      :label (label db propagation)
      :blockers (app.delete/blockers resource-type db propagation))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [label (label db resource)
            result (app.delete/delete! resource-type db resource (:user/id viewer))]
        (if (error.i/error? result)
          (assoc (render-dialog db resource) :status 422)
          (-> (http/see-other propagation.routes/index)
              (flash/success (str "Deleted " label)))))

      (render-dialog db resource))))
