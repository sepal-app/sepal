(ns sepal.app.routes.accession.detail
  (:require [reitit.core :as r]
            [sepal.app.http-response :as http]))

(defn handler [{:keys [context ::r/router]}]
  (let [{:keys [resource]} context]
    (http/found router :accession/detail-general {:id (:accession/id resource)})))
