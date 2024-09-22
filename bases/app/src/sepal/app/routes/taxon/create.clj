(ns sepal.app.routes.taxon.create
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.params :as params]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.form :as form]
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
                    :values values)])

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('taxon-form:submit')"}
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
                 :footer (form/footer :buttons (footer-buttons))
                 :page-title "Create taxon"
                 :router router)
      (html/render-html)))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [taxon (taxon.i/create! tx data)]
        (taxon.activity/create! tx taxon.activity/created created-by taxon)
        taxon))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:name :string]
   [:author [:maybe :string]]
   [:parent-id [:maybe :int]]
   [:rank :string]])

(defn handler [{:keys [context params form-params request-method viewer ::r/router]}]
  (let [{:keys [db]} context
        org (:organization context)]
    (case request-method
      :post
      (let [data (->  (params/decode FormParams form-params)
                      (assoc :organization-id (:organization/id org)))
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
