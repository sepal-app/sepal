(ns sepal.app.routes.setup.activity
  "Activity logging for setup wizard."
  (:require [sepal.activity.interface :as activity.i])
  (:import [java.time Instant]))

(def completed :setup/completed)

(def SetupCompletedData
  [:map
   [:completed-by :string]])

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :data data}))

(defmethod activity.i/data-schema completed [_]
  SetupCompletedData)
