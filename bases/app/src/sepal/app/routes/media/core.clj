(ns sepal.app.routes.media.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.media.s3 :as s3]
            [sepal.app.routes.taxon.detail :as detail]
            ))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   [middleware/require-org-membership :org-id]
   ["/s3" {:name :media/s3
           :handler #'s3/handler}]
   ["/:id/" {:name :media/detail
             :handler #'detail/handler
             ;; :middleware [[middleware/resource-loader taxon.i/get-by-id :id]
             ;;              [middleware/require-resource-org-membership :taxon/organization-id]]
             }]])
