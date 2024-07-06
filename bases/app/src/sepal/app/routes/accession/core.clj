(ns sepal.app.routes.accession.core
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.detail :as detail]
            [sepal.app.routes.accession.detail.media :as detail-media]
            [sepal.app.routes.accession.detail.general :as detail-general]))

(def accession-loader
  (middleware/default-loader accession.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ;; TODO: require permission middlware
   ["/:id" {:middleware [[middleware/resource-loader accession-loader]
                         [middleware/require-resource-org-membership :accession/organization-id]]}
    ["/" {:name :accession/detail
          :handler #'detail/handler}]
    ["/general/" {:name :accession/detail-general
                  :handler #'detail-general/handler}]
    ["/media/" {:name :accession/detail-media
                :handler #'detail-media/handler}]]])
