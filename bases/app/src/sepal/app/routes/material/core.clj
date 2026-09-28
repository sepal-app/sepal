(ns sepal.app.routes.material.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.material.create :as create]
            [sepal.app.routes.material.delete :as delete]
            [sepal.app.routes.material.detail :as detail]
            [sepal.app.routes.material.detail.general :as detail-general]
            [sepal.app.routes.material.detail.media :as detail-media]
            [sepal.app.routes.material.detail.observations :as detail-observations]
            [sepal.app.routes.material.detail.tags :as detail-tags]
            [sepal.app.routes.material.export :as export]
            [sepal.app.routes.material.index :as index]
            [sepal.app.routes.material.next-code :as next-code]
            [sepal.app.routes.material.panel :as panel]
            [sepal.app.routes.material.routes :as routes]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.permission :as material.perm]))

(def material-loader
  (middleware/default-loader material.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  [""
   ["/"
    {:name routes/index
     :permission material.perm/view
     :get #'index/handler}]
   ["/export/"
    {:name routes/export
     :permission material.perm/view
     :conflicting true
     :get #'export/handler}]
   ["/next-code/"
    {:name routes/next-code
     :conflicting true
     :permission material.perm/edit
     :handler #'next-code/handler}]
   ["/new/"
    {:name routes/new
     :permission material.perm/create
     :handler #'create/handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader material-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :permission material.perm/view
          :get #'detail/handler}]
    ["/general/" {:name routes/detail-general
                  :permission material.perm/edit
                  :permission-redirect routes/detail
                  :handler #'detail-general/handler}]
    ["/media/" {:name routes/detail-media
                :permission material.perm/edit
                :permission-redirect routes/detail
                :handler #'detail-media/handler}]
    ["/observations/" {:name routes/detail-observations
                       :permission material.perm/edit
                       :permission-redirect routes/detail
                       :handler #'detail-observations/handler}]
    ["/observations/:observation-id/" {:name routes/detail-observation
                                       :permission material.perm/edit
                                       :permission-redirect routes/detail
                                       :handler #'detail-observations/observation-handler}]
    ["/tags/" {:name routes/detail-tags
               :permission material.perm/edit
               :permission-redirect routes/detail
               :handler #'detail-tags/handler}]
    ["/tags/:tag-id/" {:name routes/detail-tag
                       :permission material.perm/edit
                       :permission-redirect routes/detail
                       :delete #'detail-tags/row-handler}]
    ["/history/" {:name routes/history
                  :permission material.perm/view
                  :get #'panel/history-handler}]
    ["/delete/" {:name routes/delete
                 :permission material.perm/delete
                 :permission-redirect routes/detail
                 :get #'delete/handler
                 :post #'delete/handler}]
    ["/panel/" {:name routes/panel
                :permission material.perm/view
                :get #'panel/handler}]]])
