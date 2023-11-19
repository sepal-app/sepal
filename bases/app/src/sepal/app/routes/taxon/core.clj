(ns sepal.app.routes.taxon.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.taxon.detail :as detail]
            [sepal.taxon.interface :as taxon.i]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   [middleware/require-org-membership :org-id]
   ["/:id/" {:name :taxon/detail
             :handler #'detail/handler
             :middleware [[middleware/resource-loader taxon.i/get-by-id :id]
                          [middleware/require-resource-org-membership :taxon/organization-id]]}]])
