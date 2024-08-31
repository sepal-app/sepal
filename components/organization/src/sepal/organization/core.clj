(ns sepal.organization.core
  (:require [camel-snake-kebab.core :as csk]
            [integrant.core :as ig]
            [malli.generator :as mg]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.organization.interface.spec :as spec]
            [sepal.store.interface :as store.i]))

;; TODO: Add a unique_id / uuid column for the organization that we use for things like s3 buckets

(defn get-by-id [db id]
  {:pre [(pos-int? id)]}
  (store.i/get-by-id db :organization id spec/Organization))

(defn create! [db data]
  (store.i/create! db :organization data spec/CreateOrganization))

(defn get-user-org [db user-id]
  ;; TODO: This is assuming one organization per user
  (some->> (db.i/execute-one! db {:select :o.*
                                  :from [[:organization :o]]
                                  :join [[:organization_user :ou]
                                         [:= :ou.organization_id :o.id]]
                                  :where [:and
                                          [:= :ou.user_id user-id]]})
           (store.i/coerce spec/Organization)))

(defn assign-role! [db data]
  (store.i/create! db :organization-user data spec/CreateOrganizationUser))

(create-ns 'sepal.organization.interface)
(alias 'org.i 'sepal.organization.interface)

(defn factory [{:keys [db] :as args}]
  (let [data (merge (mg/generate spec/CreateOrganization)
                    args)
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::org.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :organization {:id (:organization/id data)}))))

(defn organization-user-factory [{:keys [db org user] :as args}]
  (let [result (assign-role! db {:organization-id (:organization/id org)
                                 :user-id (:user/id user)
                                 :role (:role args)})]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::org.i/organization-user-factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)
          data (update data :organization-user/role (comp db.i/->pg-enum
                                                          csk/->kebab-case-string))]
      (jdbc.sql/delete! db :organization-user data))))
