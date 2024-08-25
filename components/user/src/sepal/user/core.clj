(ns sepal.user.core
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.experimental.time.generator]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]
            [sepal.user.interface.spec :as spec]
            [sepal.validation.interface :refer [invalid? validate]]))

(defn get-by-id
  [db id]
  {:pre [(pos-int? id)]}
  (store.i/get-by-id db :public.user id spec/User))

(defn create! [db data]
  (cond
    (invalid? spec/CreateUser data)
    (validate spec/CreateUser data)

    :else
    (let [data (assoc data :password [:crypt (:password data) [:gen_salt "bf"]])]
      (db.i/execute-one! db {:insert-into [:public.user]
                             :values [data]
                             :returning [:*]}))))

(defn exists? [db id-or-email]
  (let [where (if (int? id-or-email)
                [:= :id id-or-email]
                [:= :email id-or-email])]
    (db.i/exists? db {:select 1
                      :from :public.user
                      :where where})))

(create-ns 'sepal.user.interface)
(alias 'user.i 'sepal.user.interface)

(defn factory [{:keys [db] :as args}]
  (let [data (-> (mg/generate spec/CreateUser)
                 (merge (m/decode spec/CreateUser args (mt/strip-extra-keys-transformer)))
                 #_(assoc :organization-id (:organization/id organization)
                          :id (:user/id created-by)))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::user.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :public.user {:id (:user/id data)}))))
