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

(defn- build-where-clause [{:keys [q status exclude-status]}]
  (let [conditions (cond-> [:and]
                     q (conj [:or
                              [:like :email (str "%" q "%")]
                              [:like :full_name (str "%" q "%")]])
                     status (conj [:= :status (name status)])
                     exclude-status (conj [:!= :status (name exclude-status)]))]
    (when (not= conditions [:and])
      conditions)))

(defn get-all
  "Returns all users, optionally filtered.
   Options:
   - :q              - Search by name or email
   - :status         - Filter by status (:active, :archived, :invited)
   - :exclude-status - Exclude users with this status"
  [db & {:keys [q status exclude-status]}]
  (let [columns (:store/columns (m/properties spec/User))]
    (->> (cond-> {:select columns
                  :from :user
                  :order-by [:full_name :email]}
           (or q status exclude-status)
           (assoc :where (build-where-clause {:q q :status status :exclude-status exclude-status})))
         (db.i/execute! db)
         (map #(store.i/coerce spec/User %)))))

(defn count-by-role
  "Returns the count of users with the given role."
  [db role]
  (db.i/count db {:select [1]
                  :from :user
                  :where [:= :role (name role)]}))

(defn get-by-role
  "Returns all users with the given role."
  [db role]
  (let [columns (:store/columns (m/properties spec/User))]
    (->> {:select columns
          :from :user
          :where [:and
                  [:= :role (name role)]
                  [:= :status "active"]]
          :order-by [:full_name :email]}
         (db.i/execute! db)
         (map #(store.i/coerce spec/User %)))))

;; NOTE: scrypt is intentionally slow (~100ms per hash) to prevent brute-force attacks.
;; This adds up in tests when many users are created. Future optimization options:
;; - Use a faster hash (e.g., bcrypt with low rounds) in test mode
;; - Pre-compute and reuse hashed passwords in test factories
;; - Create users without passwords when not testing auth flows
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
  (when-let [user (->> {:select [:id :email :password :full_name :role :status]
                        :from :user
                        :where [:and
                                [:= :email email]
                                [:= :status "active"]]}
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

(defn factory [{:keys [db status] :as args}]
  (let [data (-> (mg/generate spec/CreateUser)
                 (merge (m/decode spec/CreateUser args (mt/strip-extra-keys-transformer)))
                 ;; Remove :id so SQLite auto-generates it, avoiding conflicts
                 ;; with IDs from tests that use create! directly
                 (dissoc :id)
                 ;; Ensure status is explicitly set for tests (default to :active)
                 (cond-> (not status) (assoc :status :active)))
        result (create! db data)]
    (vary-meta result assoc :db db)))

(defmethod ig/halt-key! ::user.i/factory [_ data]
  (when data
    (let [{:keys [db]} (meta data)]
      (jdbc.sql/delete! db :user {:id (:user/id data)}))))
