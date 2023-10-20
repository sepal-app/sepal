(ns sepal.app.routes.org.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.org.create :as create]
            [sepal.app.routes.org.detail :as detail]
            [sepal.app.routes.org.index :as index]
            [sepal.app.routes.taxon.index :as taxon.index]))

;; (def bind-organization [handler])

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/" {:name :org/index
         :handler #'index/handler}]
   ["/create" {:name :org/create
               :handler #'create/handler}]
   ["/:org-id" {:middleware [[middleware/require-org-membership :org-id]]}
    ["/" {:name :org/detail
          :handler #'detail/handler}]
    #_["/activity/" {:name :taxon/index
                   :handler #'activity.index/handler}]
    #_["/accession/" {:name :taxon/index
                    :handler #'accession.index/handler}]
    #_["/item/" {:name :taxon/index
               :handler #'item.index/handler}]
    ["/taxon/" {:name :org/taxa
               :handler #'taxon.index/handler}]
    #_["/location/" {:name :location/index
                   :handler #'taxon.index/handler}]]])
