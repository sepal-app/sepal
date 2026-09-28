(ns sepal.app.routes.accession.core
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.permission :as accession.perm]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.create :as create]
            [sepal.app.routes.accession.delete :as delete]
            [sepal.app.routes.accession.detail :as detail]
            [sepal.app.routes.accession.detail.collection :as detail-collection]
            [sepal.app.routes.accession.detail.collection-delete :as detail-collection-delete]
            [sepal.app.routes.accession.detail.general :as detail-general]
            [sepal.app.routes.accession.detail.media :as detail-media]
            [sepal.app.routes.accession.detail.notes :as detail-notes]
            [sepal.app.routes.accession.detail.tags :as detail-tags]
            [sepal.app.routes.accession.export :as export]
            [sepal.app.routes.accession.index :as index]
            [sepal.app.routes.accession.next-code :as next-code]
            [sepal.app.routes.accession.panel :as panel]
            [sepal.app.routes.accession.provenance-suggestion :as provenance-suggestion]
            [sepal.app.routes.accession.routes :as routes]))

(def accession-loader
  (middleware/default-loader accession.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  [""
   ["/"
    {:name routes/index
     :permission accession.perm/view
     :handler #'index/handler}]
   ["/export/"
    {:name routes/export
     :permission accession.perm/view
     :conflicting true
     :handler #'export/handler}]
   ["/next-code/"
    {:name routes/next-code
     :conflicting true
     :permission accession.perm/create
     :handler #'next-code/handler}]
   ["/provenance-suggestion/"
    {:name routes/provenance-suggestion
     :conflicting true
     :permission accession.perm/create
     :handler #'provenance-suggestion/handler}]
   ["/new/"
    {:name routes/new
     :permission accession.perm/create
     :handler #'create/handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader accession-loader]]
            :parameters {:path {:id nat-int?}}
            :conflicting true}
    ["/" {:name routes/detail
          :permission accession.perm/view
          :handler #'detail/handler}]
    ["/general/" {:name routes/detail-general
                  :permission accession.perm/edit
                  :permission-redirect routes/detail
                  :handler #'detail-general/handler}]
    ["/collection/" {:name routes/detail-collection
                     :permission accession.perm/edit
                     :permission-redirect routes/detail
                     :handler #'detail-collection/handler}]
    ["/collection/delete/" {:name routes/detail-collection-delete
                            :permission accession.perm/edit
                            :permission-redirect routes/detail
                            :get #'detail-collection-delete/handler
                            :post #'detail-collection-delete/handler}]
    ["/media/" {:name routes/detail-media
                :permission accession.perm/edit
                :permission-redirect routes/detail
                :handler #'detail-media/handler}]
    ["/notes/" {:name routes/detail-notes
                :permission accession.perm/edit
                :permission-redirect routes/detail
                :handler #'detail-notes/handler}]
    ["/notes/:note-id/" {:name routes/detail-note
                         :permission accession.perm/edit
                         :permission-redirect routes/detail
                         :handler #'detail-notes/note-handler}]
    ["/tags/" {:name routes/detail-tags
               :permission accession.perm/edit
               :permission-redirect routes/detail
               :handler #'detail-tags/handler}]
    ["/tags/:tag-id/" {:name routes/detail-tag
                       :permission accession.perm/edit
                       :permission-redirect routes/detail
                       :delete #'detail-tags/row-handler}]
    ["/delete/" {:name routes/delete
                 :permission accession.perm/delete
                 :permission-redirect routes/detail
                 :get #'delete/handler
                 :post #'delete/handler}]
    ["/panel/" {:name routes/panel
                :permission accession.perm/view
                :handler #'panel/handler}]]])
