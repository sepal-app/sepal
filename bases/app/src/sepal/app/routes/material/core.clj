(ns sepal.app.routes.material.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.material.bulk :as bulk]
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
            [sepal.material.interface.permission :as material.perm]
            [sepal.observation.interface.permission :as observation.perm]))

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
   ["/bulk/status/"
    {:name routes/bulk-status
     :permission material.perm/edit
     :conflicting true
     :post #'bulk/status-handler}]
   ["/bulk/move/"
    {:name routes/bulk-move
     :permission material.perm/edit
     :conflicting true
     :post #'bulk/move-handler}]
   ["/bulk/observation/"
    {:name routes/bulk-observation
     :feature :observations
     :permission observation.perm/create
     :conflicting true
     :post #'bulk/observation-handler}]
   ["/bulk/tags/"
    {:name routes/bulk-tags
     :feature :tags
     :permission material.perm/edit
     :conflicting true
     :post #'bulk/tags-add-handler}]
   ["/bulk/tags/remove/"
    {:name routes/bulk-tags-remove
     :feature :tags
     :permission material.perm/edit
     :conflicting true
     :get #'bulk/tags-remove-form-handler
     :post #'bulk/tags-remove-handler}]
   ["/:id" {:middleware [[middleware/resource-loader material-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :permission material.perm/view
          :get #'detail/handler}]
    ["/general/" {:name routes/detail-general
                  :permission material.perm/edit
                  :permission-redirect routes/detail
                  :get #'detail-general/get-handler
                  :post #'detail-general/post-handler}]
    ["/media/" {:name routes/detail-media
                :feature :media
                :permission material.perm/edit
                :permission-redirect routes/detail
                :handler #'detail-media/handler}]
    ["/observations/" {:name routes/detail-observations
                       :feature :observations
                       :permission material.perm/edit
                       :permission-redirect routes/detail
                       :get #'detail-observations/get-handler
                       :post {:permission observation.perm/create
                              :handler #'detail-observations/create-handler}}]
    ["/observations/:observation-id/" {:name routes/detail-observation
                                       :feature :observations
                                       :post {:permission observation.perm/edit
                                              :handler #'detail-observations/update-handler}
                                       :delete {:permission observation.perm/delete
                                                :handler #'detail-observations/delete-handler}}]
    ["/tags/" {:name routes/detail-tags
               :feature :tags
               :permission material.perm/edit
               :permission-redirect routes/detail
               :get #'detail-tags/get-handler
               :post #'detail-tags/post-handler}]
    ["/tags/:tag-id/" {:name routes/detail-tag
                       :feature :tags
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
