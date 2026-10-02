(ns sepal.activity.interface
  (:refer-clojure :exclude [type])
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.data.json :as json]
            [clojure.string :as str]
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

(def ^:private ^java.time.format.DateTimeFormatter stored-created-at
  "created_at as written: UTC to the millisecond, at a fixed width, so text
  order is time order. Instant.toString drops trailing zero groups, and
  '...:00Z' sorted after '...:00.500Z'. Still ISO-8601, so an older build's
  Instant/parse reads it."
  (-> (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      (.withZone java.time.ZoneOffset/UTC)))

(defn- encode-created-at [^java.time.Instant instant]
  (.format stored-created-at instant))

(defn- decode-created-at
  "An Instant from created_at. Also reads SQLite's datetime('now'),
  '2006-10-11 09:33:25', which the column default and an import write."
  [s]
  (if (string? s)
    (if (str/includes? s "T")
      (java.time.Instant/parse s)
      (java.time.Instant/parse (str (str/replace s " " "T") "Z")))
    s))
(def created-by pos-int?)
(def type :keyword)

;; Which record the event is about. Null for the two types that have no
;; subject, settings/updated and setup/completed. Unlike :activity/type this is
;; a bare keyword rather than a namespaced one, so name and keyword are enough
;; to move it in and out of the text column.
(def resource-type [:maybe :keyword])
(def resource-id [:maybe pos-int?])

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
   [:activity/created-at {:decode/store decode-created-at} created-at]
   [:activity/created-by created-by]
   [:activity/resource-type {:decode/store #(some-> % keyword)
                             :encode/store #(some-> % name)}
    resource-type]
   [:activity/resource-id resource-id]])

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
     [:created-at {:encode/store encode-created-at
                   :decode/store decode-created-at}
      created-at]
     [:created-by created-by]
     ;; Optional because the create schema is closed and two types --
     ;; settings/updated and setup/completed -- have no subject to name.
     [:resource-type {:optional true
                      :decode/store #(some-> % keyword)
                      :encode/store #(some-> % name)}
      resource-type]
     [:resource-id {:optional true} resource-id]]
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

(defn- resource-match
  "Predicate selecting the events whose subject is this record."
  [resource-type resource-id]
  [:and
   [:= :a.resource_type (name resource-type)]
   [:= :a.resource_id resource-id]])

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
  (->> (db.i/execute! db {:select [:a.* :u.id :u.email]
                          :from [[:activity :a]]
                          :join [[:user :u] [:= :u.id :a.created_by]]
                          :where (resource-match resource-type resource-id)
                          :order-by [[:a.created_at :desc] [:a.id :desc]]
                          :limit limit
                          :offset offset})
       (mapv (fn [row]
               (let [;; Extract user fields before decoding activity
                     user {:user/id (:user/id row)
                           :user/email (:user/email row)}
                     ;; Keep only activity fields for decoding
                     activity-row (select-keys row [:activity/id :activity/type
                                                    :activity/data :activity/created-at
                                                    :activity/created-by
                                                    :activity/resource-type
                                                    :activity/resource-id])]
                 (-> (m/decode Activity activity-row store.i/transformer)
                     (assoc :activity/user user)))))))

(defn count-by-resource
  "Count activities for a specific resource.

   Options:
   - :resource-type - Keyword like :taxon, :accession, :material, :location
   - :resource-id   - The resource's ID"
  [db & {:keys [resource-type resource-id]}]
  (db.i/count db {:select [:a.id]
                  :from [[:activity :a]]
                  :where (resource-match resource-type resource-id)}))
