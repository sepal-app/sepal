(ns sepal.app.routes.location.core
  (:require [sepal.app.middleware :as middleware]
            [sepal.app.routes.location.detail :as detail]
            [sepal.location.interface :as location.i]))

(def location-loader
  (middleware/default-loader location.i/get-by-id
                             :id
                             parse-long))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/:id/" {:name :location/detail
             :handler #'detail/handler
             :middleware [[middleware/resource-loader location-loader]
                          [middleware/require-resource-org-membership :location/organization-id]]}]])
