(ns sepal.app.server
  (:require [integrant.core :as ig]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :as jetty]))

(def routes
  [["/" (fn [_]
          {:status 200
           :body "hi"})]])

(def default-router-options
  {:data {:middleware [;wrap-auth
                       ;; wrap-context
                       ]}})

(defmethod ig/init-key ::server [_ cfg]
  (tap> "init server")
  (-> routes
      (ring/router default-router-options)
      (ring/ring-handler (ring/create-default-handler) {:inject-match? false})
      (jetty/run-jetty {:port (:port cfg)
                        :join? false})))

(defmethod ig/halt-key! ::server [_ server]
  (jetty/stop-server server))
