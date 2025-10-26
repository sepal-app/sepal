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
   [:location-id spec/id]
   [:location-name spec/name]
   [:location-code spec/code]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :data {:location-id (:location/id data)
                                  :location-name (:location/name data)
                                  :location-code (:location/code data)}})
      (update :activity/data #(store.i/coerce LocationActivityData %))))

(defmethod activity.i/data-schema created [_]
  LocationActivityData)

(defmethod activity.i/data-schema updated [_]
  LocationActivityData)

(defmethod activity.i/data-schema deleted [_]
  LocationActivityData)
