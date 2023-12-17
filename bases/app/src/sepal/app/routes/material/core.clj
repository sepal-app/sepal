(ns sepal.app.routes.material.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.material.detail :as detail]
            [sepal.material.interface :as material.i]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/:id/" {:name :material/detail
             :handler #'detail/handler
             :middleware [[middleware/resource-loader material.i/get-by-id :id]
                          [middleware/require-resource-org-membership :material/organization-id]]}]])
