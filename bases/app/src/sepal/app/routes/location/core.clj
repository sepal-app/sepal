(ns sepal.app.routes.location.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.location.create :as create]
            [sepal.app.routes.location.detail :as detail]
            [sepal.app.routes.location.index :as index]
            [sepal.app.routes.location.panel :as panel]
            [sepal.app.routes.location.routes :as routes]
            [sepal.location.interface :as location.i]))

(def location-loader
  (middleware/default-loader location.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/"
    {:name routes/index
     :handler #'index/handler}]
   ["/new/"
    {:name routes/new
     :middleware [[middleware/require-editor-or-admin]]
     :handler #'create/handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader location-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :handler #'detail/handler}]
    ["/panel/" {:name routes/panel
                :handler #'panel/handler}]]])
