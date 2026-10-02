(ns sepal.app.routes.media.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.media.delete :as delete]
            [sepal.app.routes.media.detail :as detail]
            [sepal.app.routes.media.detail.link :as link]
            [sepal.app.routes.media.index :as index]
            [sepal.app.routes.media.panel :as panel]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.media.s3 :as s3]
            [sepal.app.routes.media.transform :as transform]
            [sepal.app.routes.media.uploaded :as uploaded]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.permission :as media.perm]))

(def media-loader (middleware/default-loader media.i/get-by-id :id parse-long))

(defn routes []
  [""
   ["/" {:name media.routes/index
         :permission media.perm/view
         :get #'index/handler}]
   ["/s3" {:name media.routes/s3
           :permission media.perm/create
           :handler #'s3/handler}]
   ["/uploaded" {:name media.routes/uploaded
                 :permission media.perm/create
                 :handler #'uploaded/handler}]
   ["/:id" {:middleware [[middleware/resource-loader media-loader]]
            :parameters {:path {:id nat-int?}}
            :conflicting true}
    ;; A reader sees the page with its actions hidden; saving needs an editor.
    ["/"
     {:name media.routes/detail
      :permission media.perm/view
      :get #'detail/get-handler
      :post {:permission media.perm/edit
             :handler #'detail/post-handler}}]
    ["/delete/" {:name media.routes/delete
                 :permission media.perm/delete
                 :get #'delete/handler
                 :post #'delete/handler}]
    ["/link/" {:name media.routes/detail-link
               :permission media.perm/edit
               :get #'link/get-handler
               :post #'link/post-handler
               :delete #'link/delete-handler}]
    ["/panel/" {:name media.routes/panel
                :permission media.perm/view
                :get #'panel/handler}]
    ["/transform" {:name media.routes/transform
                   :permission media.perm/view
                   :get #'transform/handler}]]])
