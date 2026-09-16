(ns sepal.app.json
  (:require [clojure.data.json :as json]))

(defn write-str [data & {:as options}]
  (json/write-str data options))

(defn parse-str [data & {:as options}]
  (json/read-str data options))

(defn js
  "This function is mostly used for passing a clojure map as a js object in html
  attributes"
  [data]
  (json/write-str data :escape-slash false))

(defn json-response
  "Create a JSON response with"
  [data]
  {:status 200
   :headers {"content-type" "application/json"}
   :body (write-str data)})

(defn picker-response
  "What an autocomplete gets back: the options it may offer, and how many
  records matched in all.

  The total is there so a list cut short can say so. Without it a picker ends
  silently at whatever it asked for, and a record past that point looks to the
  reader like one the garden does not have — which is how a location nobody
  could find got created a second time."
  [options total]
  (json-response {:options (vec options) :total total}))

