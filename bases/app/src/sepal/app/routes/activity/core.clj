(ns sepal.app.routes.activity.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.activity.index :as index]
            [sepal.app.routes.activity.routes :as routes]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]
       :name routes/index
       :handler #'index/handler}])
