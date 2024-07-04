(ns sepal.app.routes.accession.create
  (:require [reitit.core :as r]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]))

(defn page-content [& {:keys [errors values router org]}]
  (accession.form/form :action (url-for router :org/accession-new {:org-id (:organization/id org)})
                       :errors errors
                       :org org
                       :router router
                       :values values))

(defn footer-buttons []
  [[:button {:class "btn btn-primary"
             :x-on:click "$refs.accessionForm.submit()"}
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
  (db.i/with-transaction [tx db]
    (let [result (accession.i/create! tx data)]
      (when-not (error.i/error? result)
        (accession.activity/create! tx accession.activity/created created-by result))
      result)))

(defn handler [{:keys [context params request-method ::r/router viewer]}]
  (let [{:keys [db]} context
        org (:current-organization context)]
    (case request-method
      :post
      (let [data (-> params
                     ;; (assoc :rank :genus)
                     (assoc :organization-id (:organization/id org)))
            result (create! db (:user/id viewer) data)]
        (if-not (error.i/error? result)
          ;; TODO: Add a success message
          (see-other router :accession/detail {:id (:accession/id result)})
          (-> (found router :org/accession-new {:org-id (:organization/id org)})
              (assoc :flash {;;:error (error.i/explain result)
                             :values data}))))

      (render :org org
              :router router
              :values params))))
