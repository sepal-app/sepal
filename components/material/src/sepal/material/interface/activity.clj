(ns sepal.material.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.material.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :material/created)
(def deleted :material/deleted)
(def updated :material/updated)

(defn create! [db type created-by material]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :data {:material-id (:material/id material)
                              :material-code (:material/code material)
                              :accession-id (:material/accession-id material)
                              :location-id (:material/location-id material)}}))

(def MaterialActivityData
  [:map
   [:material-id spec/id]
   [:material-code spec/code]
   [:accession-id spec/accession-id]
   [:location-id spec/location-id]])

(defmethod activity.i/data-schema created [_]
  MaterialActivityData)

(defmethod activity.i/data-schema updated [_]
  MaterialActivityData)

(defmethod activity.i/data-schema deleted [_]
  MaterialActivityData)
