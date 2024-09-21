(ns sepal.app.routes.auth.logout
  (:require [ring.util.http-response :refer [found]]))

(defn handler [_]
  (-> (found "/login")
      (assoc :session nil)))
