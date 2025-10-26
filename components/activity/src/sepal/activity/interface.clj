(ns sepal.activity.interface
  (:refer-clojure :exclude [type])
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.data.json :as json]
            [clojure.walk :as walk]
            [malli.core :as m]
            [malli.experimental.time :as met]
            [malli.registry :as mr]
            [malli.util :as mu]
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
   [:activity/data {:decode/store #(cond-> %
                                     (string? %) (json/read-str)
                                     :always (walk/keywordize-keys))}
    ;; TODO: I couldn't figure out how to use the data schema multimethod to automatically
    ;; validate the activity data and to make it use to store decoders so for now we'll
    ;; just do that as an extra step in each of the resource activity create! functions,
    ;; e.g. see sepal.taxon.interface.activity/create!
    [:map-of :keyword :any]]
   [:activity/created-at {:decode/store
                          #(cond-> %
                             (string? %) java.time.Instant/parse)} created-at]
   [:activity/created-by created-by]])

(defn build-create-activity-schema [type data-schema registry]
  (mu/closed-schema
    [:map
     [:type {:decode/store keyword
             :encode/store #(format "%s/%s" (namespace %) (name %))}
      [:= type]]
     [:data {:encode/store (fn [d]
                             (json/write-str d))
             :decode/store #(cond-> %
                              (string? %) (->> (json/read-str)
                                               (mapv (partial cske/transform-keys csk/->kebab-case-keyword))))}
      data-schema]
     [:created-at {:encode/store str
                   :decode/store #(cond-> %
                                    (string? %)
                                    java.time.Instant/parse)}
      created-at]
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
        (build-create-activity-schema type ds registry)))))

(defn create! [db activity]
  (let [CreateActivity (m/schema (into [:multi {:dispatch :type
                                                :lazy-refs true}]
                                       (keys (methods data-schema)))
                                 {:registry registry})]
    (store.i/create! db :activity activity CreateActivity Activity)))
