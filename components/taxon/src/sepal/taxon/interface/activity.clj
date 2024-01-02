(ns sepal.taxon.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.taxon.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :taxon/created)
(def deleted :taxon/deleted)
(def updated :taxon/updated)

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :organization-id (:taxon/organization-id data)
                       ;; TODO: The parent name would be helpful
                       :data {:taxon-id (:taxon/id data)
                              :taxon-name (:taxon/name data)
                              :taxon-author (:taxon/author data)
                              :taxon-rank (:taxon/tank data)}}))

(def TaxonActivityData
  [:map
   [:taxon-id spec/id]
   [:taxon-name spec/name]
   [:taxon-author spec/author]
   [:taxon-rank spec/rank]])

(defmethod activity.i/data-schema created [_]
  TaxonActivityData)

(defmethod activity.i/data-schema updated [_]
  TaxonActivityData)

(defmethod activity.i/data-schema deleted [_]
  TaxonActivityData)
