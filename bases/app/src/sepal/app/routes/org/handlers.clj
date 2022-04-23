(ns sepal.app.routes.org.handlers
  (:require [sepal.app.routes.org.create :as create]
            [sepal.app.routes.org.index :as index]
            [sepal.app.routes.org.detail :as detail]))

(defn index-handler [req]
  (index/handler req))

(defn create-handler [req]
  (create/handler req))

(defn new-handler [req]
  (create/handler req))

(defn detail-handler [req]
  (detail/handler req))
