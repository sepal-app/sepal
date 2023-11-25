(ns sepal.app.routes.media.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.media.s3 :as s3]
            [sepal.app.routes.media.uploaded :as uploaded]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/s3" {:name :media/s3
           :handler #'s3/handler}]
   ["/uploaded" {:name :media/uploaded
                :handler #'uploaded/handler}]
   #_["/:id/" {:name :media/detail
             :handler #'detail/handler
             ;; :middleware [[middleware/resource-loader taxon.i/get-by-id :id]
             ;;              [middleware/require-resource-org-membership :taxon/organization-id]]
             }]])
