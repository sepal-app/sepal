(ns sepal.app.routes.propagation.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.propagation.create :as create]
            [sepal.app.routes.propagation.detail :as detail]
            [sepal.app.routes.propagation.export :as export]
            [sepal.app.routes.propagation.index :as index]
            [sepal.app.routes.propagation.panel :as panel]
            [sepal.app.routes.propagation.product :as product]
            [sepal.app.routes.propagation.routes :as routes]
            [sepal.propagation.interface :as propagation.i]
            [sepal.propagation.interface.permission :as propagation.perm]))

(def propagation-loader
  (middleware/default-loader propagation.i/get-by-id
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
   ["/new/"
    {:name routes/new
     :middleware [[middleware/require-editor-or-admin]]
     :handler #'create/handler
     :conflicting true}]
   ["/parent-plant/"
    {:name routes/parent-plant
     :middleware [[middleware/require-editor-or-admin]]
     :handler #'create/parent-plant-handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader propagation-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :handler #'detail/handler}]
    ["/panel/" {:name routes/panel
                :handler #'panel/handler}]
    ["/status/" {:name routes/status
                 :middleware [[(middleware/require-permission-or-redirect
                                 propagation.perm/edit (constantly routes/detail))]]
                 :post #'detail/status-handler}]
    ["/product/" {:name routes/product
                  :middleware [[(middleware/require-permission-or-redirect
                                  propagation.perm/edit (constantly routes/detail))]]
                  :post #'product/handler}]]])
