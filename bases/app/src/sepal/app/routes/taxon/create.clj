(ns sepal.app.routes.taxon.create
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]))

(defn page-content [& {:keys [errors values router org]}]
  [:div
   (taxon.form/form :action (url-for router org.routes/taxa-new {:org-id (:organization/id org)})
                    :errors errors
                    :org org
                    :router router
                    ;; :x-on:change "console.log('changed)'"

                    :values values)])

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$refs.taxonForm.submit()"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn render [& {:keys [errors org router values]}]
  (-> (page/page :attrs {:x-data "taxonFormData"}
                 :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :values values)
                 :footer (ui.form/footer :buttons (footer-buttons))
                 :page-title "Create taxon"
                 :router router)
      (html/render-html)))

(defn create! [db created-by data]
  (db.i/with-transaction [tx db]
    (let [result (taxon.i/create! tx data)]
      (when-not (error.i/error? result)
        (taxon.activity/create! tx taxon.activity/created created-by result))
      result)))

(defn handler [{:keys [context params request-method viewer ::r/router]}]
  (let [{:keys [db]} context
        org (:current-organization context)]
    (case request-method
      :post
      (let [data (assoc params :organization-id (:organization/id org))
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :taxon/detail {:id (:taxon/id result)})
          (-> (found router org.routes/taxa-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values params))))
