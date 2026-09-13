(ns sepal.settings.interface.activity
  (:require [sepal.activity.interface :as activity.i])
  (:import [java.time Instant]))

(def updated :settings/updated)

;; An import is one event carrying the per-table counts, not one per row. A
;; loaded row was not an act of curation, and the history import brings the
;; real record of what happened to these rows.
(def import-completed :settings/import-completed)

(def SettingsActivityData
  [:map
   [:changes [:map-of :keyword :any]]])

(def ImportCompletedData
  [:map
   [:counts [:map-of :string :int]]])

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :data data}))

(defmethod activity.i/data-schema updated [_]
  SettingsActivityData)

(defmethod activity.i/data-schema import-completed [_]
  ImportCompletedData)
