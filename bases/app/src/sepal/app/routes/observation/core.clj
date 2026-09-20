(ns sepal.app.routes.observation.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.observation.detail :as detail]
            [sepal.app.routes.observation.export :as export]
            [sepal.app.routes.observation.index :as index]
            [sepal.app.routes.observation.routes :as routes]
            [sepal.observation.interface :as observation.i]))

(def observation-loader
  (middleware/default-loader observation.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/"
    {:name routes/index
     :handler #'index/handler}]
   ["/export/"
    {:name routes/export
     :conflicting true
     :handler #'export/handler}]
   ["/:id" {:middleware [[middleware/resource-loader observation-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :handler #'detail/handler}]]])
