(ns sepal.app.main
  (:require #_[clojure.tools.logging :as log]
   [malli.experimental.time :as met]
            [malli.core :as m]
            [malli.registry :as mr]
            [sepal.app.system :as system])
  (:gen-class))

;; Make sure we have the malli.experimental.time schemes in the default
;; registry.
(mr/set-default-registry!
 (mr/composite-registry
  (m/default-schemas)
  (met/schemas)))

(set! *warn-on-reflection* true)

(def default-environment "local")

(defn- get-env-variable [name default-val]
  (or (System/getenv name) default-val))

(defn -main [& _]
  (try
    (let [profile (-> "SEPAL_ENVIRONMENT"
                      (get-env-variable default-environment)
                      keyword)]
      (system/start! profile))
    (catch Exception exc
      (println (ex-message exc))
      (println exc))))
