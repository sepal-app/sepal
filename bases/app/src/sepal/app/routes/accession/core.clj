(ns sepal.app.routes.accession.core
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.create :as create]
            [sepal.app.routes.accession.detail :as detail]
            [sepal.app.routes.accession.detail.general :as detail-general]
            [sepal.app.routes.accession.detail.media :as detail-media]
            [sepal.app.routes.accession.index :as index]
            [sepal.app.routes.accession.routes :as routes]))

(def accession-loader
  (middleware/default-loader accession.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/"
    {:name routes/index
     :handler #'index/handler}]
   ["/new/"
    {:name routes/new
     :handler #'create/handler
     :conflicting true}]
   ["/:id" {:middleware [[middleware/resource-loader accession-loader]]
            :parameters {:path {:id nat-int?}}
            :conflicting true}
    ["/" {:name routes/detail
          :handler #'detail/handler}]
    ["/general/" {:name routes/detail-general
                  :handler #'detail-general/handler}]
    ["/media/" {:name routes/detail-media
                :handler #'detail-media/handler}]]])
