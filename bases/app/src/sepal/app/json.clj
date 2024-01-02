(ns sepal.app.json
  (:require [clojure.data.json :as json]))

(defn write-str [data & {:as options}]
  (json/write-str data options))

(defn js
  "This function is mostly used for passing js object in html attributes"
  [data]
  (json/write-str data :escape-slash false))

(defn json-response
  "Create a JSON response with"
  [data]
  {:status 200
   :headers {"content-type" "application/json"}
   :body (write-str data)})
