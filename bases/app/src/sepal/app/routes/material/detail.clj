(ns sepal.app.routes.material.detail
  (:require [reitit.core :as r]
            [sepal.app.http-response :as http]))

(defn handler [{:keys [context ::r/router]}]
  (let [{:keys [resource]} context]
    (http/found router :material/detail-general {:id (:material/id resource)})))
