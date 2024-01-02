(ns sepal.accession.interface.activity
  (:require [sepal.accession.interface.spec :as spec]
            [sepal.activity.interface :as activity.i])
  (:import [java.time Instant]))

(def created :accession/created)
(def deleted :accession/deleted)
(def updated :accession/updated)

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :organization-id (:accession/organization-id data)
                       ;; TODO: The parent name would be helpful
                       :data {:accession-id (:accession/id data)
                              :accession-code (:accession/code data)
                              :taxon-id (:accession/taxon-id data)}}))

(def AccessionActivityData
  [:map
   [:accession-id spec/id]
   [:accession-code spec/code]
   [:taxon-id spec/taxon-id]
   [:taxon-name :string]])

(defmethod activity.i/data-schema created [_]
  AccessionActivityData)

(defmethod activity.i/data-schema updated [_]
  AccessionActivityData)

(defmethod activity.i/data-schema deleted [_]
  AccessionActivityData)
