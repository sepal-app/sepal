(ns sepal.app.routes.dashboard.index
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.activity.routes :as activity.routes]))

(defn handler [{:keys [] :as req}]
  (tap> "*** dashboard")
  ;; TODO: For now we just redirect to the activity page but we should create a specific dashboard page
  (http/found activity.routes/index))
