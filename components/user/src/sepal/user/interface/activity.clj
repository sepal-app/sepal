(ns sepal.user.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.user.interface.spec :as spec])
  (:import [java.time Instant]))

;; Activity types
(def created :user/created)
(def updated :user/updated)

;; Data schema - records the user and what was updated
(def UserActivityData
  [:map
   [:user-email spec/email]
   [:role {:optional true} spec/role]
   [:status {:optional true} spec/status]])

(defn create!
  "Log a user update activity. Pass the user and any changed fields."
  [db created-by user changes]
  (activity.i/create! db
                      {:type updated
                       :created-at (Instant/now)
                       :created-by created-by
                       :resource-type :user
                       :resource-id (:user/id user)
                       :data (merge {:user-email (:user/email user)}
                                    changes)}))

(defn create-user!
  "Log that an account was created.

  `created-by` is the viewer who did it. Where nobody did -- the setup wizard,
  which runs before anyone can sign in -- it is the new user's own id, because
  `activity.created_by` is `not null` and the new account is the only actor
  there is.

  Three `user.i/create!` call sites deliberately do not call this:
  `instance.clj`'s two managed-provisioning paths and `cli.clj`'s create-user
  command. They run with no session at all, so there is no actor to attribute
  to and no id to borrow. A garden provisioned by the control plane, or a user
  made from the CLI, has no creation event."
  [db created-by user]
  (activity.i/create! db
                      {:type created
                       :created-at (Instant/now)
                       :created-by created-by
                       :resource-type :user
                       :resource-id (:user/id user)
                       :data (cond-> {:user-email (:user/email user)}
                               (:user/role user) (assoc :role (:user/role user))
                               (:user/status user) (assoc :status (:user/status user)))}))

;; Register data schema with activity system
(defmethod activity.i/data-schema created [_]
  UserActivityData)

(defmethod activity.i/data-schema updated [_]
  UserActivityData)
