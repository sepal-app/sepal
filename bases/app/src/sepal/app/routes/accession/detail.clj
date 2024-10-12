(ns sepal.app.routes.accession.detail
  (:require [sepal.app.http-response :as http]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [resource]} context]
    (http/found :accession/detail-general {:id (:accession/id resource)})))
