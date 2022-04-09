(ns user
  (:require [integrant.core :as ig]
            [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [sepal.config.interface :as config]))

(defn prep-app []
  (integrant.repl/set-prep! (fn []
                              (let [cfg (-> "app/system.edn"
                                            (config/read-config {}))]
                                (ig/load-namespaces cfg)
                                (ig/prep cfg)))))

(prep-app)
