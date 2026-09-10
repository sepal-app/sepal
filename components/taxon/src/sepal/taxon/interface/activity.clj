(ns sepal.taxon.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :taxon/created)
(def deleted :taxon/deleted)
(def updated :taxon/updated)

(def TaxonActivityData
  [:map
   [:taxon-name spec/name]
   [:taxon-author [:maybe spec/author]]
   [:taxon-rank {:decode/store keyword}
    spec/rank]
   [:changes {:optional true} [:sequential :string]]])

(defn create!
  ([db type created-by data]
   (create! db type created-by data nil))
  ([db type created-by data changes]
   (-> (activity.i/create! db
                           {:type type
                            :created-at (Instant/now)
                            :created-by created-by
                            :resource-type :taxon
                            :resource-id (:taxon/id data)
                          ;; TODO: The parent name would be helpful
                            :data (cond-> {:taxon-name (:taxon/name data)
                                           :taxon-author (:taxon/author data)
                                           :taxon-rank (:taxon/rank data)}
                                    changes (assoc :changes changes))})
       (update :activity/data #(store.i/coerce TaxonActivityData %)))))

(defmethod activity.i/data-schema created [_]
  TaxonActivityData)

(defmethod activity.i/data-schema updated [_]
  TaxonActivityData)

(defmethod activity.i/data-schema deleted [_]
  TaxonActivityData)
