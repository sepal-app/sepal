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

(defn validate-form-values [spec values]
  (try
    (m/coerce spec
              values
              (mt/transformer mt/strip-extra-keys-transformer {:name :form}))
    (catch Exception e
      (error.i/ex->error e))))

(defn humanize
  [err]
  (-> err error.i/data :explain me/humanize))

(def email-re #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
