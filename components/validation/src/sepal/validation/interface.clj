(ns sepal.validation.interface
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [sepal.error.interface :as error.i]))

(defn as-error [data]
  (with-meta data {:error true}))

(defn validate [spec data]
  (some-> (me/humanize (m/explain spec data))
          (as-error)))

(defn invalid? [spec data]
  (not (m/validate spec data)))

(defn error? [data]
  (-> data (meta) :error some?))

(def form-transformer
  "Transformer for decoding and validating form params.
   Converts string keys to keywords, coerces string values to proper types,
   applies default values, and strips extra keys."
  (mt/transformer
    (mt/key-transformer {:decode keyword})
    {:name :form}
    mt/strip-extra-keys-transformer
    mt/default-value-transformer
    mt/string-transformer))

(defn validate-form-values [spec values]
  (try
    (m/coerce spec values form-transformer)
    (catch Exception e
      (error.i/ex->error e))))

(defn humanize
  [err]
  (-> err error.i/data :explain me/humanize))

(def email-re #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

(defn coerce-int [v]
  (try
    (cond
      (int? v) v
      (string? v) (Integer/parseInt v)
      (nil? v) v
      :else (int v))
    (catch Exception _
      nil)))

(defn empty->nil
  "Converts empty strings to nil. Useful as a form decoder for optional fields."
  [s]
  (when (seq s) s))
