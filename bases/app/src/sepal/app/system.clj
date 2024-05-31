(ns sepal.app.system
  (:require [integrant.core :as ig]
            [malli.core :as m]
            [malli.experimental.time :as met]
            [malli.registry :as mr]
            [sepal.config.interface :as config.i]))

(set! *warn-on-reflection* true)

(def ^:dynamic *system* nil)

;; Make sure we have the malli.experimental.time schemes in the default
;; registry.
(mr/set-default-registry!
 (mr/composite-registry
  (m/default-schemas)
  (met/schemas)))

(defn start! [profile]
  (let [system-config (config.i/read-config "app/system.edn"
                                            {:profile profile})]
    (ig/load-namespaces system-config)
    (alter-var-root #'*system* (constantly (ig/init system-config)))))

(defn stop! [system]
  (ig/halt! system))
