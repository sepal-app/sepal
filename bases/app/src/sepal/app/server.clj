(ns sepal.app.server
  (:require [integrant.core :as ig]
            [ring.adapter.jetty9 :as jetty]))

(defmethod ig/init-key ::jetty [_ {:keys [handler port join?]}]
  (jetty/run-jetty handler {:port port
                            :join? join?}))

(defmethod ig/halt-key! ::jetty [_ server]
  (jetty/stop-server server))
