(ns sepal.app.cli.activity
  "Activity logging for `load-import`.

  Here rather than in a component for the same reason `routes/setup/activity`
  is: the event is about the garden as a whole, has no subject record, and
  belongs to a feature the base owns. The CLI is its only writer."
  (:require [sepal.activity.interface :as activity.i])
  (:import [java.time Instant]))

;; An import is one event carrying the per-table counts, not one per row. A
;; loaded row was not an act of curation, and the history import brings the
;; real record of what happened to these rows.
(def completed :import/completed)

(def ImportCompletedData
  [:map
   [:counts [:map-of :string :int]]])

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :data data}))

(defmethod activity.i/data-schema completed [_]
  ImportCompletedData)
