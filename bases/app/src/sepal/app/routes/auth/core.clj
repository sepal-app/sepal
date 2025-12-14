(ns sepal.app.routes.auth.core
  (:require [sepal.app.routes.auth.forgot-password :as forgot-password]
            [sepal.app.routes.auth.login :as login]
            [sepal.app.routes.auth.logout :as logout]
            #_[sepal.app.routes.auth.register :as register]
            [sepal.app.routes.auth.reset-password :as reset-password]
            [sepal.app.routes.auth.routes :as auth.routes]))

(defn routes []
  [["/login" {:name auth.routes/login
              :handler #'login/handler}]
   ["/logout" {:name auth.routes/logout
               :handler #'logout/handler}]
   ;; Registration disabled - users created via CLI (see sepal.app.cli)
   #_["/register" {:name auth.routes/register
                   :handler #'register/handler}]
   ["/forgot-password" {:name auth.routes/forgot-password
                        :handler #'forgot-password/handler}]
   ["/reset-password" {:name auth.routes/reset-password
                       :handler #'reset-password/handler}]])
