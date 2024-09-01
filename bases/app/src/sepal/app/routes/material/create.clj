(ns sepal.app.routes.material.create
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.material.form :as material.form]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]))

(defn page-content [& {:keys [errors values router org]}]
  (material.form/form :action (url-for router org.routes/materials-new {:org-id (:organization/id org)})
                      :errors errors
                      :org org
                      :router router
                      :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$refs.materialForm.submit()"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn render [& {:keys [errors org router values]}]
  (-> (page/page :attrs {:x-data "materialFormData"}
                 :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :values values)
                 :footer (ui.form/footer :buttons (footer-buttons))
                 :page-title "Create material"
                 :router router)
      (html/render-html)))

(defn create! [db created-by data]
  (db.i/with-transaction [tx db]
    (try
      (let [acc (material.i/create! tx data)]
        (material.activity/create! tx material.activity/created created-by acc)
        acc)
      (catch Exception ex
        (error.i/ex->error ex)))))

(defn handler [{:keys [context params request-method ::r/router viewer]}]
  (let [{:keys [db]} context
        org (:current-organization context)]
    (case request-method
      :post
      (let [data (-> params
                     (assoc :organization-id (:organization/id org)))
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :material/detail {:id (:material/id result)})
          (-> (found router org.routes/materials-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values params))))
