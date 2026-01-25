(ns sepal.logging.interface
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [taoensso.telemere :as tel]
            [taoensso.telemere.tools-logging :refer [tools-logging->telemere!]]))

(defmethod ig/init-key ::logging [_ {:keys [level]}]
  ;; Send clojure.tools.logging messages to telemere
  (tools-logging->telemere!)
  (when-let [lvl (some-> level str/lower-case keyword)]
    ;; Set min level for all signal kinds (nil) in sepal.* namespaces
    (tel/set-min-level! nil "sepal.*" lvl)))
