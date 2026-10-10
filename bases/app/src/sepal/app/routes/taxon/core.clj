(ns sepal.app.routes.taxon.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.taxon.bulk :as bulk]
            [sepal.app.routes.taxon.create :as create]
            [sepal.app.routes.taxon.delete :as delete]
            [sepal.app.routes.taxon.detail :as detail]
            [sepal.app.routes.taxon.detail.media :as detail-media]
            [sepal.app.routes.taxon.detail.name :as detail-name]
            [sepal.app.routes.taxon.detail.notes :as detail-notes]
            [sepal.app.routes.taxon.detail.synonyms :as detail-synonyms]
            [sepal.app.routes.taxon.detail.tags :as detail-tags]
            [sepal.app.routes.taxon.export :as export]
            [sepal.app.routes.taxon.index :as index]
            [sepal.app.routes.taxon.panel :as panel]
            [sepal.app.routes.taxon.parent-suggestion :as parent-suggestion]
            [sepal.app.routes.taxon.parentage-row :as parentage-row]
            [sepal.app.routes.taxon.rank-guess :as rank-guess]
            [sepal.app.routes.taxon.routes :as routes]
            [sepal.note.interface.permission :as note.perm]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.permission :as taxon.perm]))

(def taxon-loader (middleware/default-loader taxon.i/get-by-id :id parse-long))

(defn routes []
  [""
   ["/" {:name routes/index
         :permission taxon.perm/view
         :get #'index/handler}]
   ["/export/" {:name routes/export
                :permission taxon.perm/view
                :conflicting true
                :get #'export/handler}]
   ["/parent-suggestion/" {:name routes/parent-suggestion
                           :permission taxon.perm/create
                           :handler #'parent-suggestion/handler
                           :conflicting true}]
   ["/parentage-row/" {:name routes/parentage-row
                       :permission taxon.perm/edit
                       :handler #'parentage-row/handler
                       :conflicting true}]
   ["/rank-guess/" {:name routes/rank-guess
                    :permission taxon.perm/create
                    :handler #'rank-guess/handler
                    :conflicting true}]
   ["/new/" {:name routes/new
             :permission taxon.perm/create
             :get #'create/get-handler
             :post #'create/post-handler
             :conflicting true}]
   ["/bulk/tags/" {:name routes/bulk-tags
                   :feature :tags
                   :permission taxon.perm/edit
                   :conflicting true
                   :post #'bulk/tags-add-handler}]
   ["/bulk/tags/remove/" {:name routes/bulk-tags-remove
                          :feature :tags
                          :permission taxon.perm/edit
                          :conflicting true
                          :get #'bulk/tags-remove-form-handler
                          :post #'bulk/tags-remove-handler}]
   ["/:id" {:middleware [[middleware/resource-loader taxon-loader]]
            :parameters {:path {:id nat-int?}}
            :conflicting true}
    ["/" {:name routes/detail
          :permission taxon.perm/view
          :get #'detail/handler}]
    ["/name/" {:name routes/detail-name
               :permission taxon.perm/edit
               :permission-redirect routes/detail
               :get #'detail-name/get-handler
               :post #'detail-name/post-handler}]
    ["/media/" {:name routes/detail-media
                :feature :media
                :permission taxon.perm/edit
                :permission-redirect routes/detail
                :handler #'detail-media/handler}]
    ["/synonyms/" {:name routes/detail-synonyms
                   :permission taxon.perm/edit
                   :permission-redirect routes/detail
                   :get #'detail-synonyms/get-handler
                   :post #'detail-synonyms/post-handler}]
    ["/synonyms/:synonym-id/" {:name routes/detail-synonym
                               :permission taxon.perm/edit
                               :permission-redirect routes/detail
                               :delete #'detail-synonyms/row-handler}]
    ["/notes/" {:name routes/detail-notes
                :permission taxon.perm/edit
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
               :feature :tags
               :permission taxon.perm/edit
               :permission-redirect routes/detail
               :get #'detail-tags/get-handler
               :post #'detail-tags/post-handler}]
    ["/tags/:tag-id/" {:name routes/detail-tag
                       :feature :tags
                       :permission taxon.perm/edit
                       :permission-redirect routes/detail
                       :delete #'detail-tags/row-handler}]
    ["/delete/" {:name routes/delete
                 :permission taxon.perm/delete
                 :permission-redirect routes/detail
                 :get #'delete/handler
                 :post #'delete/handler}]
    ["/panel/" {:name routes/panel
                :permission taxon.perm/view
                :get #'panel/handler}]]])
