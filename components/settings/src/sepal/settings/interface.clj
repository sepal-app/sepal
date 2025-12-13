(ns sepal.settings.interface
  (:require [sepal.settings.core :as core]))

(defn get-value
  "Get a single setting value by key. Returns nil if not found."
  [db key]
  (core/get-value db key))

(defn get-values
  "Get multiple settings by key prefix (e.g. 'organization' returns all organization.* settings).
   Returns a map of key -> value."
  [db prefix]
  (core/get-values db prefix))

(defn set-value!
  "Set a single setting value."
  [db key value]
  (core/set-value! db key value))

(defn set-values!
  "Set multiple settings at once. Takes a map of key -> value."
  [db settings]
  (core/set-values! db settings))
