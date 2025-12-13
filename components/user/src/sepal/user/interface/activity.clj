(ns sepal.user.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.user.interface.spec :as spec])
  (:import [java.time Instant]))

(def updated :user/updated)

(def UserActivityData
  [:map
   [:user-id spec/id]
   [:changes {:optional true} [:map-of :keyword :any]]])

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :data data}))

(defmethod activity.i/data-schema updated [_]
  UserActivityData)
