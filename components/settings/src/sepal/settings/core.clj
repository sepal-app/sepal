(ns sepal.settings.core
  (:require [sepal.database.interface :as db.i]))

(defn get-value
  "Get a single setting value by key. Returns nil if not found."
  [db key]
  (-> (db.i/execute-one! db {:select [:value]
                             :from :settings
                             :where [:= :key key]})
      :settings/value))

(defn get-values
  "Get multiple settings by key prefix (e.g. 'organization' returns all organization.* settings).
   Returns a map of key -> value."
  [db prefix]
  (->> (db.i/execute! db {:select [:key :value]
                          :from :settings
                          :where [:like :key (str prefix ".%")]})
       (into {} (map (fn [{:settings/keys [key value]}] [key value])))))

(defn set-value!
  "Set a single setting value."
  [db key value]
  (db.i/execute-one! db {:insert-into :settings
                         :values [{:key key :value value}]
                         :on-conflict [:key]
                         :do-update-set {:value value}}))

(defn set-values!
  "Set multiple settings at once. Takes a map of key -> value."
  [db settings]
  (doseq [[key value] settings]
    (set-value! db key value)))
