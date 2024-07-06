(ns sepal.app.routes.material.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.material.detail :as detail]
            [sepal.app.routes.material.detail.general :as detail-general]
            [sepal.app.routes.material.detail.media :as detail-media]
            [sepal.material.interface :as material.i]))

(def material-loader
  (middleware/default-loader material.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/:id" {:middleware [[middleware/resource-loader material-loader]
                         [middleware/require-resource-org-membership :material/organization-id]]}
    ["/" {:name :material/detail
          :handler #'detail/handler}]
    ["/general/" {:name :material/detail-general
                  :handler #'detail-general/handler}]
    ["/media/" {:name :material/detail-media
                :handler #'detail-media/handler}]]])
