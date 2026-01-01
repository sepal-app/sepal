(ns sepal.user.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.user.interface.spec :as spec])
  (:import [java.time Instant]))

;; Activity types
(def updated :user/updated)

;; Data schema - records the user and what was updated
(def UserActivityData
  [:map
   [:user-id spec/id]
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
                       :data (merge {:user-id (:user/id user)
                                     :user-email (:user/email user)}
                                    changes)}))

;; Register data schema with activity system
(defmethod activity.i/data-schema updated [_]
  UserActivityData)
