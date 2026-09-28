(ns sepal.app.routes.observation.core
  (:require [sepal.app.authorization :as authz]
            [sepal.app.routes.observation.export :as export]
            [sepal.app.routes.observation.index :as index]
            [sepal.app.routes.observation.routes :as routes]))

(defn routes []
  [""
   ["/"
    {:name routes/index
     :permission authz/observation-view
     :handler #'index/handler}]
   ["/export/"
    {:name routes/export
     :permission authz/observation-view
     :handler #'export/handler}]])
