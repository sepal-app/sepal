(ns sepal.app.routes.taxon.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.taxon.create :as create]
            [sepal.app.routes.taxon.detail :as detail]
            [sepal.app.routes.taxon.detail.media :as detail-media]
            [sepal.app.routes.taxon.detail.name :as detail-name]
            [sepal.app.routes.taxon.index :as index]
            [sepal.app.routes.taxon.routes :as routes]
            [sepal.taxon.interface :as taxon.i]))

(def taxon-loader (middleware/default-loader taxon.i/get-by-id :id parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/" {:name routes/index
         :handler #'index/handler}]
   ["/new/" {:name routes/new
             :get #'create/get-handler
             :post #'create/post-handler
             :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader taxon-loader]]
            :parameters {:path {:id nat-int?}}
            :conflicting true}
    ["/" {:name routes/detail
          :handler #'detail/handler}]
    ["/name/" {:name routes/detail-name
               :handler #'detail-name/handler}]
    ["/media/" {:name routes/detail-media
                :handler #'detail-media/handler}]]])
