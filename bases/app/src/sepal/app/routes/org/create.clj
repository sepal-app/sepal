(ns sepal.app.routes.org.create
  (:require [reitit.core :as r]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]
            [sepal.organization.interface.activity :as org.activity]
            [sepal.validation.interface :refer [error?]]))

(defn form [& {:keys [errors router values]}]
  (form/form
    {:method "post"
     :action (url-for router org.routes/create)}
    [(form/anti-forgery-field)
     (form/field :label "Name"
                 :name "name"
                 :errors (:name errors)
                 :input [:input {:autocomplete "off"
                                 :class "spl-input"
                                 :x-validate.required true
                                 :id "name"
                                 :name "name"
                                 :type "text"
                                 :value (:name values)}])

     (form/field :label "Short name"
                 :name "short-name"
                 :errors (:short-name errors)
                 :input [:input {:autocomplete "off"
                                 :class "spl-input"
                                 :x-validate.required true
                                 :id "short-name"
                                 :name "short-name"
                                 :type "text"
                                 :value (:short-name values)}])

     (form/field :label "Abbreviation"
                 :name "abbreviation"
                 :errors (:abbreviation errors)
                 :input [:input {:autocomplete "off"
                                 :class "spl-input"
                                 :x-validate.required true
                                 :id "abbreviation"
                                 :name "abbreviation"
                                 :type "text"
                                 :value (:abbreviation values)}])

     [:div {:class "spl-btn-grp mt-4"}
      ;; TODO: After submitting rewrite the history to not allow the back button.
      ;; Can probably use htmx for this.
      (form/submit-button "Create organization")]]))

(defn render [& {:keys [router values]}]
  (-> (page/page :router router
                 :content (form :router router
                                :values values))
      (html/render-html)))

(defn create! [db created-by data]
  (try
    (db.i/with-transaction [tx db]
      (let [org (org.i/create! tx data)]
        (org.i/assign-role! db
                            {:organization-id (:organization/id org)
                             :user-id created-by
                             :role :owner})
        (org.activity/create! tx org.activity/created created-by org)
        org))
    (catch Exception ex
      (error.i/ex->error ex))))

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   [:name :string]
   [:short-name :string]
   [:abbreviation :string]])

(defn handler [{:keys [context form-params request-method ::r/router viewer]}]
  (let [{:keys [db]} context
        {:keys [name short-name abbreviation] :as data}  (params/decode FormParams form-params)]
    (case request-method
      :post
      (let [result (create! db
                            (:user/id viewer)
                            {:name name
                             :short-name short-name
                             :abbreviation abbreviation})]
        (if-not (error? result)
          (http/found router org.routes/activity {:org-id (-> result :organization/id str)})
          (-> (http/see-other router org.routes/create)
              (flash/set-field-errors result)
              (assoc-in [:flash :values] data))))

      ;; else
      (render :router router
              :values data))))
