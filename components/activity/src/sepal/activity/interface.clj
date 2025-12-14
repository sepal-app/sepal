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

(defn- resource-id-json-path
  "Returns the JSON path for extracting a resource ID from activity data.
   E.g., :taxon -> '$.taxon-id'"
  [resource-type]
  (str "$." (name resource-type) "-id"))

(defn get-by-resource
  "Get activities for a specific resource.
   Returns activities with user info, ordered by created_at desc.

   Options:
   - :resource-type - Keyword like :taxon, :accession, :material, :location
   - :resource-id   - The resource's ID
   - :limit         - Max activities to return (default 10)
   - :offset        - Offset for pagination (default 0)"
  [db & {:keys [resource-type resource-id limit offset]
         :or {limit 10 offset 0}}]
  (let [json-path (resource-id-json-path resource-type)]
    (->> (db.i/execute! db {:select [:a.* :u.id :u.email]
                            :from [[:activity :a]]
                            :join [[:user :u] [:= :u.id :a.created_by]]
                            :where [:= [[:cast [:->> :a.data json-path] :integer]]
                                    resource-id]
                            :order-by [[:a.created_at :desc]]
                            :limit limit
                            :offset offset})
         (mapv (fn [row]
                 (let [;; Extract user fields before decoding activity
                       user {:user/id (:user/id row)
                             :user/email (:user/email row)}
                       ;; Keep only activity fields for decoding
                       activity-row (select-keys row [:activity/id :activity/type
                                                      :activity/data :activity/created-at
                                                      :activity/created-by])]
                   (-> (m/decode Activity activity-row store.i/transformer)
                       (assoc :activity/user user))))))))

(defn count-by-resource
  "Count activities for a specific resource.

   Options:
   - :resource-type - Keyword like :taxon, :accession, :material, :location
   - :resource-id   - The resource's ID"
  [db & {:keys [resource-type resource-id]}]
  (let [json-path (resource-id-json-path resource-type)]
    (db.i/count db {:select [:id]
                    :from [:activity]
                    :where [:= [[:cast [:->> :data json-path] :integer]]
                            resource-id]})))
