(ns sepal.app.routes.dashboard.core
  (:require [sepal.app.authorization :as authz]
            [sepal.app.routes.dashboard.index :as index]
            [sepal.app.routes.dashboard.routes :as routes]))

(defn routes []
  ["" {:name routes/index
       :permission authz/activity-view
       :get #'index/handler}])
