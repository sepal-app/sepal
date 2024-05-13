(ns sepal.app.routes.taxon.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.taxon.detail :as detail]
            [sepal.taxon.interface :as taxon.i]))

(def taxon-loader (middleware/default-loader taxon.i/get-by-id :id))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   [middleware/require-org-membership :org-id]
   ["/:id/" {:name :taxon/detail
             :handler #'detail/handler
             :middleware [[middleware/resource-loader taxon-loader]
                          ;; TODO: Only request org membership if the org is not null
                          ;; [middleware/require-resource-org-membership :taxon/organization-id]
                          ]}]])
