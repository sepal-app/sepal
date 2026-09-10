(ns sepal.accession.interface.activity
  (:require [sepal.accession.interface.spec :as spec]
            [sepal.activity.interface :as activity.i]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :accession/created)
(def deleted :accession/deleted)
(def updated :accession/updated)

;; The accession's own id is the subject and lives in activity.resource_id.
;; :taxon-id stays: it is context, not the subject, and it is the only record
;; of which taxon the accession carried at the time.
;; The accession's own id is the subject and lives in activity.resource_id.
;; :taxon-id stays: it is context, not the subject, and it is the only record
;; of which taxon the accession carried at the time.
(def AccessionActivityData
  [:map
   [:accession-code spec/code]
   [:taxon-id spec/taxon-id]])

(defn create! [db type created-by accession]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type :accession
                           :resource-id (:accession/id accession)
                           :data {:accession-code (:accession/code accession)
                                  :taxon-id (:accession/taxon-id accession)}})
      (update :activity/data #(store.i/coerce AccessionActivityData %))))

(defmethod activity.i/data-schema created [_]
  AccessionActivityData)

(defmethod activity.i/data-schema updated [_]
  AccessionActivityData)

(defmethod activity.i/data-schema deleted [_]
  AccessionActivityData)
