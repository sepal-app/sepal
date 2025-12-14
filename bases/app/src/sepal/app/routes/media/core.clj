(ns sepal.app.routes.media.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.media.detail :as detail]
            [sepal.app.routes.media.detail.link :as link]
            [sepal.app.routes.media.index :as index]
            [sepal.app.routes.media.panel :as panel]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.media.s3 :as s3]
            [sepal.app.routes.media.uploaded :as uploaded]
            [sepal.media.interface :as media.i]))

(def media-loader (middleware/default-loader media.i/get-by-id :id parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/" {:name media.routes/index
         :handler #'index/handler}]
   ["/s3" {:name media.routes/s3
           :middleware [[middleware/require-editor-or-admin]]
           :handler #'s3/handler}]
   ["/uploaded" {:name media.routes/uploaded
                 :middleware [[middleware/require-editor-or-admin]]
                 :handler #'uploaded/handler}]
   ["/:id" {:middleware [[middleware/resource-loader media-loader]]
            :parameters {:path {:id nat-int?}}
            :conflicting true}
    ["/"
     {:name media.routes/detail
      :middleware [[middleware/require-editor-or-admin]]
      :get #'detail/handler
      :delete #'detail/handler}]
    ["/link/" {:name media.routes/detail-link
               :middleware [[middleware/require-editor-or-admin]]
               :handler #'link/handler}]
    ["/panel/" {:name media.routes/panel
                :handler #'panel/handler}]]])
