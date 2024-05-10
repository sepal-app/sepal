(ns sepal.activity.interface
  ;; (:refer-clojure :exclude [find])
  (:require [clojure.walk :as walk]
            [malli.core :as m]
            [malli.registry :as mr]
            [malli.util :as mu]
            [sepal.database.interface :as db.i]))

(defmulti data-schema (fn [type] type))

(def Activity
  [:map
   [:activity/type {:decode/db keyword
                    :encode/db #(format "%s/%s" (namespace %) (name %))}
    :keyword]
   [:activity/data {:decode/db walk/keywordize-keys}
    ;; TODO: Can we validate this against the data-schema
    ;; multimethod?
    [:map-of :keyword :any]]
   [:activity/created-at :time/instant]
   [:activity/created-by :int]
   [:activity/organization-id :int]])

(defn build-create-activity-schema [data-schema registry]
  (mu/closed-schema
   [:map
    [:type {:decode/db keyword
            :encode/db #(format "%s/%s" (namespace %) (name %))}
     [:= type]]
    [:data {;;:encode/json db.i/->jsonb
            :encode/db db.i/->jsonb}
     data-schema]
    [:created-at :time/instant]
    [:created-by :int]
    [:organization-id :int]]
   {:registry registry}))

(def registry
  (mr/lazy-registry
   (m/default-schemas)
   (fn [type registry]
     ;; Create the schema lazily depending on the :type of the activity
     (when-let [ds (data-schema type)]
       (build-create-activity-schema ds registry)))))

(defn create! [db activity]
  (let [Activity (m/schema (into [:multi {:dispatch :type :lazy-refs true}]
                                 (keys (methods data-schema)))
                           {:registry registry})
        value (m/encode Activity activity db.i/transformer)]
    ;; TODO: seems like we should validate before we encode
    (m/validate Activity activity)
    (db.i/execute! db {:insert-into :activity
                       :values [value]})))

#_(defn find [db]
    (let [result (db.i/execute! db {:select :* :from :activity})]
      (mapv #(m/coerce Activity % db.i/transformer)
            result)))
