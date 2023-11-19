(ns sepal.app.routes.org.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.create :as accession.create]
            [sepal.app.routes.accession.index :as accession.index]
            [sepal.app.routes.org.create :as create]
            [sepal.app.routes.org.detail :as detail]
            [sepal.app.routes.org.index :as index]
            [sepal.app.routes.taxon.create :as taxon.create]
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
    ["/taxa" {:name :org/taxa
               :handler #'taxon.index/handler}]
    ["/taxa/new" {:name :org/taxa-new
                  :handler #'taxon.create/handler}]

    ["/accessions" {:name :org/accessions
                    :handler #'accession.index/handler}]
    ["/accessions/new" {:name :org/accessions-new
                        :handler #'accession.create/handler}]

    #_["/location/" {:name :location/index
                   :handler #'taxon.index/handler}]]])
