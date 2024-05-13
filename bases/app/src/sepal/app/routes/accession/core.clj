(ns sepal.app.routes.accession.core
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.detail :as detail]))

(def accession-loader
  (middleware/default-loader accession.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ;; TODO: require permission middlware
   ["/:id/" {:name :accession/detail
             :handler #'detail/handler
             :middleware [;;[middleware/resource-loader accession.i/get-by-id :id]
                          [middleware/resource-loader accession-loader]
                          [middleware/require-resource-org-membership :accession/organization-id]]}]])
