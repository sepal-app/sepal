(ns sepal.app.routes.accession.core
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.permission :as accession.perm]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.bulk :as bulk]
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
            [sepal.app.routes.accession.routes :as routes]
            [sepal.note.interface.permission :as note.perm]))

(def accession-loader
  (middleware/default-loader accession.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  [""
   ["/"
    {:name routes/index
     :permission accession.perm/view
     :get #'index/handler}]
   ["/export/"
    {:name routes/export
     :permission accession.perm/view
     :conflicting true
     :get #'export/handler}]
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
   ["/bulk/tags/"
    {:name routes/bulk-tags
     :permission accession.perm/edit
     :conflicting true
     :post #'bulk/tags-add-handler}]
   ["/bulk/tags/remove/"
    {:name routes/bulk-tags-remove
     :permission accession.perm/edit
     :conflicting true
     :get #'bulk/tags-remove-form-handler
     :post #'bulk/tags-remove-handler}]
   ["/:id" {:middleware [[middleware/resource-loader accession-loader]]
            :parameters {:path {:id nat-int?}}
            :conflicting true}
    ["/" {:name routes/detail
          :permission accession.perm/view
          :get #'detail/handler}]
    ["/general/" {:name routes/detail-general
                  :permission accession.perm/edit
                  :permission-redirect routes/detail
                  :get #'detail-general/get-handler
                  :post #'detail-general/post-handler}]
    ["/collection/" {:name routes/detail-collection
                     :permission accession.perm/edit
                     :permission-redirect routes/detail
                     :get #'detail-collection/get-handler
                     :post #'detail-collection/post-handler}]
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
                :get #'detail-notes/get-handler
                :post {:permission note.perm/create
                       :handler #'detail-notes/create-handler}}]
    ["/notes/:note-id/" {:name routes/detail-note
                         :post {:permission note.perm/edit
                                :handler #'detail-notes/update-handler}
                         :delete {:permission note.perm/delete
                                  :handler #'detail-notes/delete-handler}}]
    ["/tags/" {:name routes/detail-tags
               :permission accession.perm/edit
               :permission-redirect routes/detail
               :get #'detail-tags/get-handler
               :post #'detail-tags/post-handler}]
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
                :get #'panel/handler}]]])
