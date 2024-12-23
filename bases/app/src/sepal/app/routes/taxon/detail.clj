(ns sepal.app.routes.taxon.detail
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [resource]} context]
    (http/found taxon.routes/detail-name {:id (:taxon/id resource)})))
