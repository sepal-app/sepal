(ns sepal.malli.interface
  (:require [integrant.core :as ig]
            [sepal.malli.core :as core]))

(defmethod ig/init-key ::init [_ _]
  (core/init))

(defn init []
  (core/init))

(defn humanize-coercion-ex
  "Return the humanized error of a malli.core/coercion exception."
  [ex]
  (core/humanize-coercion-ex ex))
