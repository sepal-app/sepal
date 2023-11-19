(ns sepal.app.routes.accession.core
  (:require ;; [sepal.app.routes.accession.detail :as edit]
            #_[sepal.app.routes.accession.index :as index]
            [sepal.accession.interface :as accession.i]
            [sepal.app.middleware :as middleware]
            ;; [sepal.app.routes.accession.create :as create]
            [sepal.app.routes.accession.detail :as detail]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ;; TODO: require permission middlware
   ["/:id/" {:name :accession/detail
             :handler #'detail/handler
             :middleware [[middleware/resource-loader accession.i/get-by-id :id]
                          [middleware/require-resource-org-membership :accession/organization-id]]}]])
