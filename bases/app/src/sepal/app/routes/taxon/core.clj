(ns sepal.app.routes.taxon.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.taxon.create :as create]
            [sepal.app.routes.taxon.edit :as edit]
            ;; [sepal.app.routes.taxon.detail :as detail]
            [sepal.app.routes.taxon.index :as index]))

(def routes
  ["/" {:middleware [[middleware/require-viewer]]}
   ["/orgs/:org-id/taxa" {:name :taxon/index
         :handler #'index/handler}]
   [""]
   ["/create" {:name :taxon/create
               :handler #'create/handler}]
   ["/:id"
    ["/" {:name :taxon/edit
          :handler #'edit/handler}]]])
