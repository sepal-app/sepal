(ns sepal.app.routes.taxon.detail
  (:require [sepal.app.http-response :as http]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [resource]} context]
    (http/found :taxon/detail-name {:id (:taxon/id resource)})))
