(ns sepal.app.routes.taxon.delete
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(def resource-type :taxon)

(defn- label [taxon]
  (str "taxon " (:taxon/name taxon)))

(defn- render-dialog [db taxon]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for taxon.routes/delete {:id (:taxon/id taxon)})
      :label (label taxon)
      :blockers (app.delete/blockers resource-type db taxon))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (app.delete/delete! resource-type db resource (:user/id viewer))]
        (if (error.i/error? result)
          (assoc (render-dialog db resource) :status 422)
          (-> (http/see-other taxon.routes/index)
              (flash/success (str "Deleted " (label resource))))))

      (render-dialog db resource))))
