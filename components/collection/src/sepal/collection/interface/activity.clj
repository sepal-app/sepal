(ns sepal.collection.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.collection.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :collection/created)
(def updated :collection/updated)

;; A collection has no page of its own -- it is a tab on the accession, one per
;; accession -- so the accession is the subject, the way a note's parent is and
;; a synonym's taxon is. The type still says a collection was edited rather
;; than a name, and the event lands on the accession's history.
;;
;; The collector and locality are the payload because they are what a curator
;; checks when they distrust a record's provenance.
(def CollectionActivityData
  [:map
   [:collection-id spec/id]
   [:collector spec/collector]
   [:locality spec/locality]])

(defn create! [db type created-by collection]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type :accession
                           :resource-id (:collection/accession-id collection)
                           :data {:collection-id (:collection/id collection)
                                  :collector (:collection/collector collection)
                                  :locality (:collection/locality collection)}})
      (update :activity/data #(store.i/coerce CollectionActivityData %))))

(defmethod activity.i/data-schema created [_]
  CollectionActivityData)

(defmethod activity.i/data-schema updated [_]
  CollectionActivityData)
