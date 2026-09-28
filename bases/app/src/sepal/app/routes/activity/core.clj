(ns sepal.app.routes.activity.core
  (:require [sepal.app.authorization :as authz]
            [sepal.app.routes.activity.index :as index]
            [sepal.app.routes.activity.routes :as routes]))

(defn routes []
  ["" {:name routes/index
       :permission authz/activity-view
       :handler #'index/handler}])
