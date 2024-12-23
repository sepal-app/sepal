(ns sepal.app.routes.material.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.material.create :as create]
            [sepal.app.routes.material.detail :as detail]
            [sepal.app.routes.material.detail.general :as detail-general]
            [sepal.app.routes.material.detail.media :as detail-media]
            [sepal.app.routes.material.index :as index]
            [sepal.app.routes.material.routes :as routes]
            [sepal.material.interface :as material.i]))

(def material-loader
  (middleware/default-loader material.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/"
    {:name routes/index
     :handler #'index/handler}]
   ["/new/"
    {:name routes/new
     :handler #'create/handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader material-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :handler #'detail/handler}]
    ["/general/" {:name routes/detail-general
                  :handler #'detail-general/handler}]
    ["/media/" {:name routes/detail-media
                :handler #'detail-media/handler}]]])
