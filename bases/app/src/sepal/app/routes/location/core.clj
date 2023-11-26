(ns sepal.app.routes.location.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.location.detail :as detail]
            [sepal.location.interface :as location.i]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/:id/" {:name :location/detail
             :handler #'detail/handler
             :middleware [[middleware/resource-loader location.i/get-by-id :id]
                          [middleware/require-resource-org-membership :location/organization-id]]}]])
