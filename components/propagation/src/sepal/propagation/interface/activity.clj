(ns sepal.propagation.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.propagation.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :propagation/created)
(def updated :propagation/updated)
(def deleted :propagation/deleted)

;; The type is what makes an entry readable in the stream: "cutting
;; propagation created" says something, "propagation created" does not. The
;; parent accession id carries the link back.
(def PropagationActivityData
  [:map
   [:propagation-type spec/type]
   [:parent-accession-id spec/parent-accession-id]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type :propagation
                           :resource-id (:propagation/id data)
                           :data {:propagation-type (:propagation/type data)
                                  :parent-accession-id (:propagation/parent-accession-id data)}})
      (update :activity/data #(store.i/coerce PropagationActivityData %))))

(defmethod activity.i/data-schema created [_]
  PropagationActivityData)

(defmethod activity.i/data-schema updated [_]
  PropagationActivityData)

(defmethod activity.i/data-schema deleted [_]
  PropagationActivityData)
