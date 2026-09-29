(ns sepal.app.routes.propagation.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.propagation.create :as create]
            [sepal.app.routes.propagation.delete :as delete]
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
  [""
   ["/"
    {:name routes/index
     :permission propagation.perm/view
     :get #'index/handler}]
   ["/export/"
    {:name routes/export
     :permission propagation.perm/view
     :conflicting true
     :get #'export/handler}]
   ["/new/"
    {:name routes/new
     :permission propagation.perm/create
     :handler #'create/handler
     :conflicting true}]
   ["/parent-plant/"
    {:name routes/parent-plant
     :permission propagation.perm/edit
     :handler #'create/parent-plant-handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader propagation-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :permission propagation.perm/view
          :get #'detail/get-handler
          :post {:permission propagation.perm/edit
                 :handler #'detail/post-handler}}]
    ["/panel/" {:name routes/panel
                :permission propagation.perm/view
                :get #'panel/handler}]
    ["/status/" {:name routes/status
                 :permission propagation.perm/edit
                 :permission-redirect routes/detail
                 :post #'detail/status-handler}]
    ["/delete/" {:name routes/delete
                 :permission propagation.perm/delete
                 :permission-redirect routes/detail
                 :get #'delete/handler
                 :post #'delete/handler}]
    ["/product/" {:name routes/product
                  :permission propagation.perm/edit
                  :permission-redirect routes/detail
                  :post #'product/handler}]]])
