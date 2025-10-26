(ns sepal.accession.interface.activity
  (:require [sepal.accession.interface.spec :as spec] [sepal.activity.interface :as activity.i]
            [sepal.store.interface :as store.i]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :accession/created)
(def deleted :accession/deleted)
(def updated :accession/updated)

(def AccessionActivityData
  [:map
   [:accession-id spec/id]
   [:accession-code spec/code]
   [:taxon-id spec/taxon-id]])

(defn create! [db type created-by accession]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :data {:accession-id (:accession/id accession)
                                  :accession-code (:accession/code accession)
                                  :taxon-id (:accession/taxon-id accession)}})
      (update :activity/data #(store.i/coerce AccessionActivityData %))))

(defmethod activity.i/data-schema created [_]
  AccessionActivityData)

(defmethod activity.i/data-schema updated [_]
  AccessionActivityData)

(defmethod activity.i/data-schema deleted [_]
  AccessionActivityData)
