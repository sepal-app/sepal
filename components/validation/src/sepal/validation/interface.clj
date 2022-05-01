(ns sepal.validation.interface
  (:require [malli.core :as m]
            [malli.error :as me]))

(defn- validation-seq
  "Create a validation-seq from humanized malli validation error.

  A validation-seq is a sequence of list of maps with a message key and optional
  field key and :error metadata key is true.
  "
  [error]
  (cond
    ;; Convert map of field errors into array of maps with keys field and message
    (map? error)
    (reduce-kv (fn [acc k v] (conj acc {:field k :message v})) [] error)

    (string? error)
    {:message error}

    (nil? error)
    {:message nil}

    ;; Convert sequence of errors into array of maps with a message key
    (seqable? error)
    (map validation-seq error)))

;; (comment
;;   (validation-error :int {:x 1})
;;   (validation-error [:map [:x :string]] {:x 1})
;;   (validation-seq ["error"])
;;   (validation-seq "error")
;;   ())

(defn validation-error [spec data]
  (-> (me/humanize (m/explain spec data))
      (validation-seq)
      (with-meta {:error true})))

(defn invalid? [spec data]
  (not (m/validate spec data)))

(defn error? [data]
  (-> data (meta) :error))

(def email-re #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
