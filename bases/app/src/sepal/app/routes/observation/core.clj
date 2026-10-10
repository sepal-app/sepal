(ns sepal.app.routes.observation.core
  (:require [sepal.app.routes.observation.export :as export]
            [sepal.app.routes.observation.index :as index]
            [sepal.app.routes.observation.routes :as routes]
            [sepal.observation.interface.permission :as observation.perm]))

(defn routes []
  [""
   {:feature :observations}
   ["/"
    {:name routes/index
     :permission observation.perm/view
     :get #'index/handler}]
   ["/export/"
    {:name routes/export
     :permission observation.perm/view
     :get #'export/handler}]])
