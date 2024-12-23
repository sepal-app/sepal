(ns sepal.app.routes.dashboard.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.dashboard.index :as index]
            [sepal.app.routes.dashboard.routes :as routes]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]
       :name routes/index
       :handler #'index/handler}])
