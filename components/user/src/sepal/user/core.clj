(ns sepal.user.core
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.experimental.time.generator]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]
            [sepal.user.interface.spec :as spec])
  (:import [com.password4j Password]))

(defn get-by-id
  [db id]
  {:pre [(pos-int? id)]}
  (store.i/get-by-id db :user id spec/User))

(defn get-by-email
  [db email]
  (some->> {:select :*
            :from :user
            :where [:= :email email]}
           (db.i/execute-one! db)
           (store.i/coerce spec/User)))

(defn- encrypt-password [password]
  (-> (Password/hash password)
      (.addRandomSalt)
      (.withScrypt)
      (.getResult)))

(defn create!
  "Create a user.

  The password key should be the unencrypted password.
  "
  [db {:keys [password] :as data}]
  (let [data (when password (update data :password encrypt-password))]
    (store.i/create! db :user data spec/CreateUser spec/User)))

(defn exists? [db email]
  (db.i/exists? db {:select 1
                    :from :user
                    :where [:= :email email]}))

(defn set-password! [db id password]
  (store.i/update! db
                   :user
                   id
                   {:password (encrypt-password password)}
                   spec/SetPassword
                   spec/User))

(defn verify-password [db email password]
  (when-let [user (->> {:select [:id :email :password :full_name]
                        :from :user
                        :where [:= :email email]}
                       (db.i/execute-one! db))]
    (when (-> (Password/check password (:user/password user))
              (.withScrypt))
      (store.i/coerce spec/User (dissoc user :user/password)))))

(defn update!
  "Update user (full_name, email)."
  [db id data]
  (store.i/update! db :user id data spec/UpdateUser spec/User))

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
      (jdbc.sql/delete! db :user {:id (:user/id data)}))))
