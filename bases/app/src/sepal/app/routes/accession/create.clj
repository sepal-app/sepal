(ns sepal.app.routes.accession.create
  (:require [reitit.core :as r]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.params :as params]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]))

(defn page-content [& {:keys [errors values router org]}]
  (accession.form/form :action (url-for router org.routes/accessions-new {:org-id (:organization/id org)})
                       :errors errors
                       :org org
                       :router router
                       :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$dispatch('accession-form:submit')"}
    "Save"]
   [:button {:class "btn btn-secondary"
             :x-on:click "dirty && confirm('Are you sure you want to lose your changes?') && history.back()"}
    "Cancel"]])

(defn render [& {:keys [errors org router values]}]
  (-> (page/page :attrs {:x-data "accessionFormData"}
                 :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :values values)
                 :footer (ui.form/footer :buttons (footer-buttons))
                 :page-title "Create accession"
                 :router router)
      (html/render-html)))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [acc (accession.i/create! tx data)]
        (accession.activity/create! tx accession.activity/created created-by acc)
        acc))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   [:code :string]
   [:taxon-id :int]])

(defn handler [{:keys [context form-params request-method ::r/router viewer]}]
  (let [{:keys [db]} context
        org (:organization context)]
    (case request-method
      :post
      (let [data (-> (params/decode FormParams form-params)
                     (assoc :organization-id (:organization/id org)))
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :accession/detail {:id (:accession/id result)})
          (-> (found router org.routes/accessions-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values form-params))))
