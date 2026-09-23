(ns sepal.app.routes.observation.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.observation.export :as export]
            [sepal.app.routes.observation.index :as index]
            [sepal.app.routes.observation.routes :as routes]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/"
    {:name routes/index
     :handler #'index/handler}]
   ["/export/"
    {:name routes/export
     :handler #'export/handler}]])
