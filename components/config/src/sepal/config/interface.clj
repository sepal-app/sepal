(ns sepal.config.interface
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defmethod aero/reader 'ig/ref
  [_opts _tag value]
  (ig/ref value))

(defmethod aero/reader 'resource
  [_opts _tag value]
  (io/resource value))

(defn read-config [config-file opts]
  (some-> (io/resource config-file)
          (aero/read-config opts)))
