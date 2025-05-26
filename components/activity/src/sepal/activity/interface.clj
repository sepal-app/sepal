(ns sepal.activity.interface
  (:refer-clojure :exclude [type])
  (:require [clojure.walk :as walk]
            [malli.core :as m]
            [malli.experimental.time :as met]
            [malli.registry :as mr]
            [malli.util :as mu]
            [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]))

(defmulti data-schema (fn [type] type))

(def id pos-int?)
(def created-at :time/instant)
(def created-by pos-int?)
(def type :keyword)

(def Activity
  [:map {:closed true}
   [:activity/id id]
   [:activity/type {:decode/store keyword
                    :encode/store #(format "%s/%s" (namespace %) (name %))}
    type]
   [:activity/data {:decode/store walk/keywordize-keys}
    ;; TODO: Can we validate this against the data-schema
    ;; multimethod?
    [:map-of :keyword :any]]
   [:activity/created-at created-at]
   [:activity/created-by created-by]])

(defn build-create-activity-schema [type data-schema registry]
  (mu/closed-schema
    [:map
     [:type {:decode/store keyword
             :encode/store #(format "%s/%s" (namespace %) (name %))}
      [:= type]]
     [:data {:encode/store db.i/->jsonb}
      data-schema]
     [:created-at created-at]
     [:created-by created-by]]
    {:registry registry}))

(def registry
  (mr/lazy-registry
    (mr/composite-registry
      (m/default-schemas)
      (met/schemas))
    (fn [type registry]
     ;; Create the schema lazily depending on the :type of the activity
      (when-let [ds (data-schema type)]
        ;; (tap> (str "ds: " ds))
        (build-create-activity-schema type ds registry)))))

(defn create! [db activity]
  (let [CreateActivity (m/schema (into [:multi {:dispatch :type
                                                :lazy-refs true}]
                                       (keys (methods data-schema)))
                                 {:registry registry})]
    (store.i/create! db :activity activity CreateActivity Activity)))
