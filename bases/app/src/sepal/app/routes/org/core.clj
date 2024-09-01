(ns sepal.app.routes.org.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.create :as accession.create]
            [sepal.app.routes.accession.index :as accession.index]
            [sepal.app.routes.activity.index :as activity.index]
            [sepal.app.routes.location.create :as location.create]
            [sepal.app.routes.location.index :as location.index]
            [sepal.app.routes.material.create :as material.create]
            [sepal.app.routes.material.index :as material.index]
            [sepal.app.routes.media.index :as media.index]
            [sepal.app.routes.org.create :as create]
            [sepal.app.routes.org.detail :as detail]
            [sepal.app.routes.org.index :as index]
            [sepal.app.routes.org.routes :as routes]
            [sepal.app.routes.taxon.create :as taxon.create]
            [sepal.app.routes.taxon.index :as taxon.index]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/" {:name routes/index
         :handler #'index/handler}]
   ["/create" {:name routes/create
               :handler #'create/handler}]
   ["/:org-id" {:middleware [[middleware/require-org-membership :org-id]]}
    ["/" {:name routes/detail
          :handler #'detail/handler}]

    ["/activity" {:name routes/activity
                  :handler #'activity.index/handler}]

    ["/locations" {:name routes/locations
                   :handler #'location.index/handler}]
    ["/locations/new" {:name routes/locations-new
                       :handler #'location.create/handler}]

    ["/materials" {:name routes/materials
                   :handler #'material.index/handler}]
    ["/materials/new" {:name routes/materials-new
                       :handler #'material.create/handler}]
    ["/media" {:name routes/media
               :handler #'media.index/handler}]
    ["/taxa" {:name routes/taxa
              :handler #'taxon.index/handler}]
    ["/taxa/new" {:name routes/taxa-new
                  :handler #'taxon.create/handler}]

    ["/accessions" {:name routes/accessions
                    :handler #'accession.index/handler}]
    ["/accessions/new" {:name routes/accessions-new
                        :handler #'accession.create/handler}]]])
