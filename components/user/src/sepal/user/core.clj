(ns sepal.user.core
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.experimental.time.generator]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]
            [sepal.user.interface.spec :as spec]))

(defn get-by-id
  [db id]
  {:pre [(pos-int? id)]}
  (store.i/get-by-id db :public.user id spec/User))

(defn get-by-email
  [db email]
  (some->> {:select :*
            :from :public.user
            :where [:= :email email]}
           (db.i/execute-one! db)
           (store.i/coerce spec/User)))

(defn create! [db data]
  (store.i/create! db :public.user data spec/CreateUser spec/User))

(defn exists? [db email]
  (db.i/exists? db {:select 1
                    :from :public.user
                    :where [:= :email email]}))

(defn set-password! [db id password]
  (store.i/update! db
                   :public.user
                   id
                   {:password password}
                   spec/SetPassword
                   spec/User))

(defn verify-password [db email password]
  (->> {:select
        :*
        :from :public.user
        :where [:and
                [:= :email email]
                [[:= :password
                  [:'crypt password :password]]]]}
       (db.i/execute-one! db)))

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
