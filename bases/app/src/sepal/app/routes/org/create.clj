(ns sepal.app.routes.org.create
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.types :as jdbc.types]
            [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.app.routes.org.form :as form]))

(defn page [& {:keys [error values]}]
  (html/root-template
   {:content
    [:div
     (form/form :method "post"
                :action "/org/create"
                :values values)
     (when error
       [:div (:message error)])]}))

(defn create! [db data]
  (let [stmt (-> {:insert-into :organization
                  :values [data]
                  :returning :*}
                 (sql/format))]
    (try
      (jdbc/execute-one! db stmt)
      (catch Exception e
        {:error {:message (ex-message e)}}))))

(defn insert-organization-user! [db data]
  (let [stmt (-> {:insert-into :organization-user
                  :values [data]
                  :returning :*}
                 (sql/format))]
    (try
      (jdbc/execute-one! db stmt)
      (catch Exception e
        {:error {:message (ex-message e)}}))))

(defn handler [{:keys [::r/router context flash params request-method viewer] :as req}]
  (if (= request-method :post)
    (let [{:keys [db]} context
          data (select-keys params [:name :short-name :abbreviation])
          org (create! db data)
          error (:error org)
          ou (when-not error (insert-organization-user! db {:organization-id (:organization/id org)
                                                            :user-id (:user/id viewer)
                                                            :role (jdbc.types/as-other "owner")}))]
      (if-not error
        (found router :org-detail {:id (-> org :organization/id str)})
        (-> (see-other router :org-new)
            (assoc :flash {:error error
                           :values data}))))
    (-> (page :error (:error flash)
              :values (:values flash))
        (html/render-html))))
