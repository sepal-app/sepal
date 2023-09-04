(ns user
  (:require [integrant.core :as ig]
            [integrant.repl :as ir]
            [integrant.repl.state :as ir.state]
            [sepal.config.interface :as config]))

(add-tap println)

(def ^:dynamic *system*)
(def ^:dynamic *db*)

(defn prep-app
  ([]
   (prep-app :local))
  ([profile]
   (ir/set-prep! (fn []
                   (let [cfg (-> "app/system.edn"
                                  (config/read-config {:profile profile}))]
                         (ig/load-namespaces cfg)
                         (ig/prep cfg))))))
(defn go
  ([]
   (go :local))
  ([profile]
   (prep-app profile)
   (ir/go)
   (alter-var-root #'*system* (constantly ir.state/system))
   (alter-var-root #'*db* (constantly (:sepal.database.interface/db ir.state/system)))))
