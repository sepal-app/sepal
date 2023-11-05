(ns sepal.app.json
  (:require [clojure.data.json :as json]))

(defn json-response [data]
  {:status 200
   :headers {"content-type" "application/json"}
   :body (json/write-str data)})
