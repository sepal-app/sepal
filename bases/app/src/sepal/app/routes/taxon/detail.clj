(ns sepal.app.routes.taxon.detail
  (:require [malli.error :as me]
            [reitit.core :as r]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.dropdown :as dropdown]
            [sepal.app.ui.alert :as alert]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.validation.interface :as validation.i]))

(defn page-title-buttons [& {:keys [org router taxon]}]
  (dropdown/dropdown "Actions"
                     (dropdown/item (url-for router
                                             :org/taxa-new
                                             {:org-id (:organization/id org)})
                                    "Add a taxon")))

(defn page-content [& {:keys [errors org router taxon values]}]
  (let [read-only? (nil? (:taxon/organization-id taxon))]
    [:div
     (when read-only?
       (alert/info "Taxa from the WFO Plantlist are not editable."))
     (taxon.form/form :action (url-for router :taxon/detail {:id (:taxon/id taxon)})
                      :errors errors
                      :org org
                      :router router
                      :read-only read-only?
                      :values values)]))

(defn render [& {:keys [errors org router taxon values]}]
  (-> (page/page :content (page-content :errors errors
                                        :org org
                                        :router router
                                        :taxon taxon
                                        :values values)
                 :page-title (:taxon/name taxon)
                 :page-title-buttons (page-title-buttons :org org
                                                         :router router
                                                         :taxon taxon)
                 :router router)
      (html/render-html)))

(defn save! [db taxon-id updated-by data]
  (db.i/with-transaction [tx db]
    (let [result (taxon.i/update! tx taxon-id data)]
      (when-not (error.i/error? result)
        (taxon.activity/create! tx taxon.activity/updated updated-by result))
      result)))


(defn handler [{:keys [context flash params request-method ::r/router viewer]}]
  (let [{:keys [db organization resource]} context]
    (case request-method
      :post
      (let [result (save! db (:taxon/id resource) (:user/id viewer) params)]
        (if-not (error.i/error? result)
          (http/found router :taxon/detail {:id (:taxon/id result)})
          (do
            (tap> (str "ERROR: " (validation.i/humanize result)))
            (-> (http/found router :taxon/detail {:id (:taxon/id resource)})
                (flash/set-field-errors (validation.i/humanize result))))))

      :get
      (let [parent (when (:taxon/parent-id resource)
                     (taxon.i/get-by-id db (:taxon/parent-id resource)))
            values {:id (:taxon/id resource)
                    :name (:taxon/name resource)
                    :rank (:taxon/rank resource)
                    :organization-id (or (:taxon/organization-id resource)
                                         (:organization/id organization))
                    :parent-id (:taxon/id parent)
                    :parent-name (:taxon/name parent)}]
        (tap> (str "FLASH:" flash))
        (render :org organization
                :router router
                :taxon resource
                :values values)))))
