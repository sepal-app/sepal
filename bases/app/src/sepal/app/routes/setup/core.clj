(ns sepal.app.routes.setup.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.setup.admin :as admin]
            [sepal.app.routes.setup.index :as index]
            [sepal.app.routes.setup.organization :as organization]
            [sepal.app.routes.setup.regional :as regional]
            [sepal.app.routes.setup.review :as review]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.server :as server]
            [sepal.app.routes.setup.taxonomy :as taxonomy]))

(defn routes []
  ["" {:middleware [[middleware/require-setup-incomplete]]}
   ["" {:name setup.routes/index
        :permission :public
        :handler #'index/handler}]
   ["/admin" {:name setup.routes/admin
              :permission :public
              :handler #'admin/handler}]
   ["" {:middleware [[middleware/require-setup-admin]]}
    ["/server" {:name setup.routes/server
                :permission :public
                :handler #'server/handler}]
    ["/organization" {:name setup.routes/organization
                      :permission :public
                      :handler #'organization/handler}]
    ["/regional" {:name setup.routes/regional
                  :permission :public
                  :handler #'regional/handler}]
    ["/taxonomy" {:name setup.routes/taxonomy
                  :permission :public
                  :handler #'taxonomy/handler}]
    ["/taxonomy/progress" {:name setup.routes/taxonomy-progress
                           :permission :public
                           :handler #'taxonomy/progress-handler}]
    ["/review" {:name setup.routes/review
                :permission :public
                :handler #'review/handler}]]])
