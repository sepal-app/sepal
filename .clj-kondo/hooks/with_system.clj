(ns hooks.with-system
  (:require [clj-kondo.hooks-api :as api]))

(defn with-system [{:keys [node]}]
  (let [[binding-vec & body] (rest (:children node))
        [sym] (:children binding-vec)]
    {:node (api/list-node
            (list*
             (api/token-node 'let)
             (api/vector-node [sym (api/token-node 'nil)])
             body))}))
