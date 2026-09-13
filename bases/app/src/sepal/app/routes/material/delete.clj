(ns sepal.app.routes.material.delete
  (:require [sepal.accession.interface :as acc.i]
            [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(def resource-type :material)

(defn- label
  "A material's code is only unique within its accession, so the accession's
  code is what makes the name in the dialog identify one plant."
  [db material]
  (let [accession (acc.i/get-by-id db (:material/accession-id material))]
    (str "material " (:accession/code accession) "." (:material/code material))))

(defn- render-dialog [db material]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for material.routes/delete {:id (:material/id material)})
      :label (label db material)
      :blockers (app.delete/blockers resource-type db material))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (app.delete/delete! resource-type db resource (:user/id viewer))]
        (if (error.i/error? result)
          (assoc (render-dialog db resource) :status 422)
          (-> (http/see-other material.routes/index)
              (flash/success (str "Deleted " (label db resource))))))

      (render-dialog db resource))))
