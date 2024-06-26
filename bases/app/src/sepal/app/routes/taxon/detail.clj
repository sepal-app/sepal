(ns sepal.app.routes.taxon.detail
  (:require [reitit.core :as r]
            [sepal.app.http-response :as http]))

(defn handler [{:keys [context ::r/router]}]
  (let [{:keys [resource]} context]
    (http/found router :taxon/detail-name {:id (:taxon/id resource)})))
