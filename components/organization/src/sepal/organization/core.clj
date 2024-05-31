(ns sepal.organization.core
  (:require [honey.sql :as sql]
            [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.organization.interface.spec :as spec]
            [sepal.validation.interface :refer [invalid? validate]]))

(defn create! [db data]
  ;; TODO: coerce then encode
  (cond
    (invalid? spec/CreateOrganization data)
    (validate spec/CreateOrganization data)

    :else
    (let [stmt (-> {:insert-into :organization
                    :values [data]
                    :returning :*}
                   (sql/format))]
      (jdbc/execute-one! db stmt))))

(defn get-user-org [db user-id]
  ;; TODO: This is assuming one organization per user
  (some->> (db.i/execute-one! db {:select :o.*
                                  :from [[:organization :o]]
                                  :join [[:organization_user :ou]
                                         [:= :ou.organization_id :o.id]]
                                  :where [:and
                                          [:= :ou.user_id user-id]]})
           (db.i/coerce spec/Organization)))

(create-ns 'sepal.organization.interface)
(alias 'org.i 'sepal.organization.interface)

(defn factory [{:keys [db] :as args}]
  (let [data (mg/generate spec/CreateOrganization)
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::org.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :organization {:id (:organization/id data)}))))
