(ns sepal.app.routes.accession.detail
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.accession.routes :as accession.routes]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [resource]} context]
    (http/found accession.routes/detail-general {:id (:accession/id resource)})))
