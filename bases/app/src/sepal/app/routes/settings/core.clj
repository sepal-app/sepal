(ns sepal.app.routes.settings.core
  (:require [sepal.app.http-response :as http]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.settings.organization :as organization]
            [sepal.app.routes.settings.profile :as profile]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.settings.security :as security]))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["" {:name settings.routes/index
        :handler (fn [_] (http/see-other settings.routes/profile))}]
   ["/profile" {:name settings.routes/profile
                :handler #'profile/handler}]
   ["/security" {:name settings.routes/security
                 :handler #'security/handler}]
   ;; Admin only
   ["/organization" {:name settings.routes/organization
                     :middleware [[middleware/require-admin]]
                     :handler #'organization/handler}]])
