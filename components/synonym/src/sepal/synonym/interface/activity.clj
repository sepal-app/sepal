(ns sepal.synonym.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.store.interface :as store.i]
            [sepal.synonym.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :synonym/created)
(def deleted :synonym/deleted)

;; A synonym hangs on a taxon, so a synonym event is a taxon event. The
;; synonym's own id stays in the payload: the row may be gone by the time
;; anyone reads the event, and nothing else records which one it was.
(def SynonymActivityData
  [:map
   [:synonym-id spec/id]
   [:synonym-name spec/synonym-name]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type :taxon
                           :resource-id (:synonym/taxon-id data)
                           :data {:synonym-id (:synonym/id data)
                                  :synonym-name (:synonym/synonym-name data)}})
      (update :activity/data #(store.i/coerce SynonymActivityData %))))

(defmethod activity.i/data-schema created [_]
  SynonymActivityData)

(defmethod activity.i/data-schema deleted [_]
  SynonymActivityData)
