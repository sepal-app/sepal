(ns sepal.signals.core
  (:require [clojure.core.async :as async]))

(defn create-signal
  [size]
  (let [c (async/chan size)]
    [c (async/mult c)]))

(defn subscribe
  [[c mc] cb size]
  (let [cs (async/chan size)]
    (async/tap mc cs)
    (async/go-loop []
      (when-let [v (async/<! cs)]
        (cb v)
        (recur)))
    [c mc cs]))

(defn unsubscribe [[_c mc cs]]
  (async/untap mc cs))

(defn unsubscribe-all [[_c mc _cs]]
  (async/untap-all mc))

(defn publish [[c _mc] value]
  (async/put! c value)
  value)
