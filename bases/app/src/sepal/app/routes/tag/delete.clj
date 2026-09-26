(ns sepal.app.routes.tag.delete
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(def resource-type :tag)

(defn- label [tag]
  (str "tag " (:tag/name tag)))

(defn- render-dialog [db tag]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for tag.routes/delete {:id (:tag/id tag)})
      :label (label tag)
      :blockers (app.delete/blockers resource-type db tag))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (app.delete/delete! resource-type db resource (:user/id viewer))]
        (if (error.i/error? result)
          (assoc (render-dialog db resource) :status 422)
          (-> (http/see-other tag.routes/index)
              (flash/success (str "Deleted " (label resource))))))

      (render-dialog db resource))))
