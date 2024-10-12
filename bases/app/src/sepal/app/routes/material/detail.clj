(ns sepal.app.routes.material.detail
  (:require [sepal.app.http-response :as http]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [resource]} context]
    (http/found :material/detail-general {:id (:material/id resource)})))
