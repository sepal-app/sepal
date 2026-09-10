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
;; :changes is optional because one schema serves created, updated and
;; deleted, and only an update has anything to diff. An update always passes
;; it, empty when nothing changed -- "we did not record it" and "nothing
;; changed" have to stay distinguishable.
(def AccessionActivityData
  [:map
   [:accession-code spec/code]
   [:taxon-id spec/taxon-id]
   [:changes {:optional true} [:sequential :string]]])

(defn create!
  ([db type created-by accession]
   (create! db type created-by accession nil))
  ([db type created-by accession changes]
   (-> (activity.i/create! db
                           {:type type
                            :created-at (Instant/now)
                            :created-by created-by
                            :resource-type :accession
                            :resource-id (:accession/id accession)
                            :data (cond-> {:accession-code (:accession/code accession)
                                           :taxon-id (:accession/taxon-id accession)}
                                    changes (assoc :changes changes))})
       (update :activity/data #(store.i/coerce AccessionActivityData %)))))

(defmethod activity.i/data-schema created [_]
  AccessionActivityData)

(defmethod activity.i/data-schema updated [_]
  AccessionActivityData)

(defmethod activity.i/data-schema deleted [_]
  AccessionActivityData)
