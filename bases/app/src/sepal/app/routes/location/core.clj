(ns sepal.app.routes.location.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.location.archive :as archive]
            [sepal.app.routes.location.create :as create]
            [sepal.app.routes.location.delete :as delete]
            [sepal.app.routes.location.detail :as detail]
            [sepal.app.routes.location.detail.general :as detail-general]
            [sepal.app.routes.location.detail.media :as detail-media]
            [sepal.app.routes.location.detail.observations :as detail-observations]
            [sepal.app.routes.location.export :as export]
            [sepal.app.routes.location.index :as index]
            [sepal.app.routes.location.panel :as panel]
            [sepal.app.routes.location.routes :as routes]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.permission :as location.perm]))

(def location-loader
  (middleware/default-loader location.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  [""
   ["/"
    {:name routes/index
     :permission location.perm/view
     :get #'index/handler}]
   ["/export/"
    {:name routes/export
     :permission location.perm/view
     :conflicting true
     :get #'export/handler}]
   ["/new/"
    {:name routes/new
     :permission location.perm/create
     :handler #'create/handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader location-loader]]
            :conflicting true}
    ["/" {:name routes/detail
          :permission location.perm/view
          :get #'detail/handler}]
    ["/general/" {:name routes/detail-general
                  :permission location.perm/edit
                  :permission-redirect routes/detail
                  :handler #'detail-general/handler}]
    ["/observations/" {:name routes/detail-observations
                       :permission location.perm/edit
                       :permission-redirect routes/detail
                       :handler #'detail-observations/handler}]
    ["/media/" {:name routes/detail-media
                :permission location.perm/edit
                :permission-redirect routes/detail
                :handler #'detail-media/handler}]
    ["/observations/:observation-id/" {:name routes/detail-observation
                                       :permission location.perm/edit
                                       :permission-redirect routes/detail
                                       :handler #'detail-observations/observation-handler}]
    ["/delete/" {:name routes/delete
                 :permission location.perm/delete
                 :permission-redirect routes/detail
                 :get #'delete/handler
                 :post #'delete/handler}]
    ;; Archiving is an edit, not a delete: it is reversible, and it is the only
    ;; way a location with a history ever leaves the garden.
    ["/archive/" {:name routes/archive
                  :permission location.perm/edit
                  :permission-redirect routes/detail
                  :get #'archive/handler
                  :post #'archive/handler}]
    ["/unarchive/" {:name routes/unarchive
                    :permission location.perm/edit
                    :permission-redirect routes/detail
                    :post #'archive/unarchive-handler}]
    ["/panel/" {:name routes/panel
                :permission location.perm/view
                :get #'panel/handler}]]])
