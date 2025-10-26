(ns sepal.logging.interface
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [taoensso.telemere] ;; setup logging side-effects
            [taoensso.telemere.tools-logging :refer [tools-logging->telemere!]]))

(defmethod ig/init-key ::logging [_ {:keys [level]}]
  ;; Send clojure.tools.logging message to telemere
  (tools-logging->telemere!)
  (taoensso.telemere/set-min-level! :default "sepal.*"
                                    (some-> level str/lower-case keyword)))
