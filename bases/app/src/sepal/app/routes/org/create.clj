(ns sepal.app.routes.org.create
  (:require [next.jdbc.types :as jdbc.types]
            [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.routes.org.form :as form]
            [sepal.organization.interface :as org.i]))

(defn page [& {:keys [error values]}]
  (html/root-template
   {:content
    [:div
     (form/form :method "post"
                :action "/org/create"
                :values values)
     (when error
       [:div (:message error)])]}))

(defn handler [{:keys [::r/router context flash params request-method viewer] :as req}]
  (if (= request-method :post)
    (let [{:keys [db]} context
          data (select-keys params [:name :short-name :abbreviation])
          ;; TODO: create the user and assign the role in the same transaction
          org (org.i/create! db data)
          error (:error org)
          ou (when-not error (org.i/assign-role db
                                                {:organization-id (:organization/id org)
                                                 :user-id (:user/id viewer)
                                                 :role (jdbc.types/as-other "owner")}))]
      (if-not error
        (found router :org-detail {:org-id (-> org :organization/id str)})
        (-> (see-other router :org-new)
            (assoc :flash {:error error
                           :values data}))))
    (-> (page :error (:error flash)
              :values (:values flash))
        (html/render-html))))
