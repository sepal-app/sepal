(ns sepal.app.routes.org.core
  (:require [sepal.app.routes.org.index :as index]
            [sepal.app.routes.org.detail :as detail]
            [sepal.app.routes.org.create :as create]))

(def routes
  [["" {:name :org/index
         :handler #'index/handler}]
   ["/create" {:name :org/create
               :handler #'create/handler}]
   ["/:org-id"
    ["/" {:name :org/detail
          :handler #'detail/handler}]]])
