(ns sepal.app.routes.propagation.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.propagation.export :as export]
            [sepal.app.routes.propagation.index :as index]
            [sepal.app.routes.propagation.routes :as routes]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/"
    {:name routes/index
     :handler #'index/handler}]
   ["/export/"
    {:name routes/export
     :conflicting true
     :handler #'export/handler}]])
