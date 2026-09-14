(ns sepal.app.routes.accession.delete
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(def resource-type :accession)

(defn- label [accession]
  (str "accession " (:accession/code accession)))

(defn- render-dialog [db accession]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for accession.routes/delete {:id (:accession/id accession)})
      :label (label accession)
      :blockers (app.delete/blockers resource-type db accession))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [result (app.delete/delete! resource-type db resource (:user/id viewer))]
        (if (error.i/error? result)
          ;; 422 rather than a redirect: the record is still there, and the
          ;; caller asked for something the server will not do. The dialog comes
          ;; back explaining why, which is what a hand-rolled POST deserves.
          (assoc (render-dialog db resource) :status 422)
          (-> (http/see-other accession.routes/index)
              (flash/success (str "Deleted " (label resource))))))

      (render-dialog db resource))))
