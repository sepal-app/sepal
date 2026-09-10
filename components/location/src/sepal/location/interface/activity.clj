(ns sepal.location.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.location.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :location/created)
(def deleted :location/deleted)
(def updated :location/updated)

(def LocationActivityData
  [:map
   [:location-name spec/name]
   [:location-code spec/code]
   [:changes {:optional true} [:sequential :string]]])

(defn create!
  ([db type created-by data]
   (create! db type created-by data nil))
  ([db type created-by data changes]
   (-> (activity.i/create! db
                           {:type type
                            :created-at (Instant/now)
                            :created-by created-by
                            :resource-type :location
                            :resource-id (:location/id data)
                            :data (cond-> {:location-name (:location/name data)
                                           :location-code (:location/code data)}
                                    changes (assoc :changes changes))})
       (update :activity/data #(store.i/coerce LocationActivityData %)))))

(defmethod activity.i/data-schema created [_]
  LocationActivityData)

(defmethod activity.i/data-schema updated [_]
  LocationActivityData)

(defmethod activity.i/data-schema deleted [_]
  LocationActivityData)
