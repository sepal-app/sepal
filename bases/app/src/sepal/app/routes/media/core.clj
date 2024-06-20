(ns sepal.app.routes.media.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.media.detail :as detail]
            [sepal.app.routes.media.detail.link :as link]
            [sepal.app.routes.media.s3 :as s3]
            [sepal.app.routes.media.uploaded :as uploaded]
            [sepal.media.interface :as media.i]))

(def media-loader (middleware/default-loader media.i/get-by-id :id parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/s3" {:name :media/s3
           :handler #'s3/handler}]
   ["/uploaded" {:name :media/uploaded
                 :handler #'uploaded/handler}]
   ["/:id/" {:name :media/detail
             :handler #'detail/handler
             :middleware [[middleware/resource-loader media-loader]
                          [middleware/require-resource-org-membership :media/organization-id]]}]
   ["/:id/link/" {:name :media/detail.link
                  :handler #'link/handler
                  :middleware [[middleware/resource-loader media-loader]
                               [middleware/require-resource-org-membership :media/organization-id]]}]])
