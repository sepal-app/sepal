(ns sepal.app.json
  (:require [clojure.data.json :as json]))

(defn write-str [data]
  (json/write-str data))

(defn json-response
  "Create a JSON response with"
  [data]
  {:status 200
   :headers {"content-type" "application/json"}
   :body (write-str data)})
