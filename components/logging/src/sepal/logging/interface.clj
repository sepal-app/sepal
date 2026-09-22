(ns sepal.logging.interface
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [taoensso.telemere :as tel]
            [taoensso.telemere.tools-logging :refer [tools-logging->telemere!]]))

(defmethod ig/init-key ::logging [_ {:keys [level]}]
  ;; Send clojure.tools.logging messages to telemere
  (tools-logging->telemere!)
  (when-let [lvl (some-> level str/lower-case keyword)]
    ;; This is global process state: every sepal.* signal in this JVM is
    ;; gated by it, not just this component's. Capture whatever governed
    ;; sepal.* before this call, so halt-key! can put it back rather than
    ;; leaving the mutation in place once this component is gone.
    (let [previous-level (:runtime (tel/get-min-levels nil "sepal.logging.interface"))]
      ;; Set min level for all signal kinds (nil) in sepal.* namespaces
      (tel/set-min-level! nil "sepal.*" lvl)
      {:previous-level previous-level})))

(defmethod ig/halt-key! ::logging [_ config]
  (when config
    (tel/set-min-level! nil "sepal.*" (:previous-level config))))
