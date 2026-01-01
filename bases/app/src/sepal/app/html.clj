(ns sepal.app.html
  (:require [clojure.string :as s]
            [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as chassis]
            [zodiac.core :as z]
            [zodiac.ext.assets :as z.assets]))

(defn attr [& classes]
  (->> classes
       flatten
       (remove nil?)
       (mapv name)
       (s/join " ")))

(defn static-url [name]
  (if-let [assets (get-in z/*request*  [::z/context ::z.assets/assets])]
    (assets name)
    (log/warn "Could not find the assets in the request context.")))

(defn render-partial
  "Return an html response without a doctype."
  [content]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (chassis/html content)})
