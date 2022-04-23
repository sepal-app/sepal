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
  ;; TODO: find the users' organizations and if we don't have a default
  ;; organization set in the cookie then
  (if (:organization session)
    (found router :org-detail {:id (-> session :organization :id)})
    (let [{:keys [db]} context
          orgs (get-user-organizations db (:user/id viewer))]
      (if (seq orgs)
        ;; TODO: create a view to allow the user to select an organization that
        ;; posts back here
        (found router :org-detail {:id (-> orgs first :organization/id)})
        (found router :org-new)))))
