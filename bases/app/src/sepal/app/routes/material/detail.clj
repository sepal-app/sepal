(ns sepal.app.routes.material.detail
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.material.routes :as material.routes]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [resource]} context]
    (http/found material.routes/detail-general {:id (:material/id resource)})))
