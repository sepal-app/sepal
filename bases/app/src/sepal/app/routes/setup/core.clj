(ns sepal.app.routes.setup.core
  (:require [sepal.app.routes.setup.admin :as admin]
            [sepal.app.routes.setup.index :as index]
            [sepal.app.routes.setup.organization :as organization]
            [sepal.app.routes.setup.regional :as regional]
            [sepal.app.routes.setup.review :as review]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.server :as server]
            [sepal.app.routes.setup.taxonomy :as taxonomy]))

(defn routes []
  ["" {}
   ["" {:name setup.routes/index
        :handler #'index/handler}]
   ["/admin" {:name setup.routes/admin
              :handler #'admin/handler}]
   ["/server" {:name setup.routes/server
               :handler #'server/handler}]
   ["/organization" {:name setup.routes/organization
                     :handler #'organization/handler}]
   ["/regional" {:name setup.routes/regional
                 :handler #'regional/handler}]
   ["/taxonomy" {:name setup.routes/taxonomy
                 :handler #'taxonomy/handler}]
   ["/review" {:name setup.routes/review
               :handler #'review/handler}]])
