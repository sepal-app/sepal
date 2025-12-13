(ns sepal.settings.interface.activity
  (:require [sepal.activity.interface :as activity.i])
  (:import [java.time Instant]))

(def updated :settings/updated)

(def SettingsActivityData
  [:map
   [:changes [:map-of :keyword :any]]])

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :data data}))

(defmethod activity.i/data-schema updated [_]
  SettingsActivityData)
