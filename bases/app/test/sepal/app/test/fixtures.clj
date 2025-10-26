(ns sepal.app.test.fixtures
  (:require [clojure.test :as t]
            [integrant.core :as ig])
  (:import [clojure.lang ExceptionInfo]))

(defn apply-fixtures [fixtures test-fn]
  (try
    (let [sys (ig/init fixtures)
          args (reduce-kv #(if (vector? %2)
                             ;; If the system key is a vector then take the name
                             ;; of the second value in the vector as test
                             ;; function arg
                             (assoc %1 (-> %2  second name keyword) %3)
                             %1)
                          {}
                          sys)]
      (test-fn args)
      (ig/halt! sys))
    (catch ExceptionInfo e
      ;; (log/error e)
      ;; Integrant doesn't halt the system when an exception occurs in one of
      ;; the init-key fns, so we have to halt the system here to give the
      ;; halt-key fns a chance to clean up.
      (when-let [s (:system (ex-data e))]
        (ig/halt! s))
      (throw e))))

(defmacro testing
  "If it looks like a test with fixtures and a fixture function then apply the
  fixtures, otherwise use a normal clojure.test/testing"
  [description & body]
  (let [[fixtures test-fn & rest] body]
    ;; TODO: Is it possible to see if fixtures resolves to a map if its a symbol
    ;; or seq
    (if (and (or (symbol? fixtures)
                 (seqable? fixtures))
             (= (first test-fn) 'fn)
             (empty? rest))
      `(t/testing ~description (apply-fixtures ~fixtures ~test-fn))
      `(t/testing ~description ~@body))))
