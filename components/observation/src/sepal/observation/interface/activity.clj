(ns sepal.observation.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.observation.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :observation/created)
(def deleted :observation/deleted)
(def updated :observation/updated)

;; The note and the value are deliberately absent. An activity feed is not a
;; second copy of the observation, and the changed-field list is the right
;; home for "the value changed".
;;
;; An observation's subject is the record it hangs on, not the observation.
;; That is what a reader of a material's history is asking for.
(def ObservationActivityData
  [:map
   [:observation-id spec/id]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type (:observation/resource-type data)
                           :resource-id (:observation/resource-id data)
                           :data {:observation-id (:observation/id data)}})
      (update :activity/data #(store.i/coerce ObservationActivityData %))))

(defmethod activity.i/data-schema created [_]
  ObservationActivityData)

(defmethod activity.i/data-schema updated [_]
  ObservationActivityData)

(defmethod activity.i/data-schema deleted [_]
  ObservationActivityData)
