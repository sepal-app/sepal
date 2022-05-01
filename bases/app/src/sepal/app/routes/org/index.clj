(ns sepal.app.routes.org.index
  (:require [sepal.app.html :as html]
            [reitit.core :as r]
            [sepal.app.http-response :refer [found]]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]))

(defn get-user-organizations [db user-id]
  (let [stmt
        (-> {:select :*
             :from [[:organization :o]]
             :join [[:organization_user :ou] [:= :ou.organization_id :o.id]]
             :where [:= :ou.user_id user-id]}
            (sql/format))]
    (jdbc/execute! db stmt)))

(defn handler [{:keys [context ::r/router session viewer] :as req}]
  (if (:organization session)
    (found router :org-detail {:org-id (-> session :organization :organization/id)})
    (let [{:keys [db]} context
          user-orgs (get-user-organizations db (:user/id viewer))
          org (first user-orgs)]
      (if org
        ;; TODO: create a view to allow the user to select an organization that
        ;; posts back here
        (found router :org-detail {:org-id (:organization/id org)})
        (found router :org-new)))))
