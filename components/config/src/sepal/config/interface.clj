(ns sepal.config.interface
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defmethod aero/reader 'ig/ref
  [_opts _tag value]
  (ig/ref value))

(defn read-config [config-file opts]
  (-> (io/resource config-file)
      (aero/read-config opts)))
