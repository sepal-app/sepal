(ns sepal.app.system
  (:require [sepal.config.interface :as config.i]
            [integrant.core :as ig]))

(def ^:dynamic *system* nil)

(defn start! [profile]
  (let [system-config (config.i/read-config "app/system.edn"
                                       {:profile profile})]
    (ig/load-namespaces system-config)
    (ig/prep system-config)
    (alter-var-root #'*system* (constantly (ig/init system-config)))))
