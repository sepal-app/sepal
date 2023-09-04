(ns sepal.validation.interface
  (:require [malli.core :as m]
            [malli.error :as me]))

(defn as-error [data]
  (with-meta data {:error true}))

(defn validate [spec data]
  (some-> (me/humanize (m/explain spec data))
          (as-error)))

(defn invalid? [spec data]
  (not (m/validate spec data)))

(defn error? [data]
  (-> data (meta) :error some?))


(def email-re #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
