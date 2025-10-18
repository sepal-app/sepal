(ns sepal.signals.interface
  (:require [sepal.signals.core :as core]))

(defn create-signal
  ([]
   (core/create-signal nil))
  ([size]
   (core/create-signal size)))

(defn subscribe
  ([signal cb]
   (core/subscribe signal cb nil))
  ([signal cb size]
   (core/subscribe signal cb size)))

(defn unsubscribe [subscription]
  (core/unsubscribe subscription))

(defn unsubscribe-all [signal]
  (core/unsubscribe signal))

(defn publish [signal value]
  (core/publish signal value))
