(ns sepal.synonym.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.store.interface :as store.i]
            [sepal.synonym.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :synonym/created)
(def deleted :synonym/deleted)

(def SynonymActivityData
  [:map
   [:synonym-id spec/id]
   [:taxon-id spec/taxon-id]
   [:synonym-name spec/synonym-name]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :data {:synonym-id (:synonym/id data)
                                  :taxon-id (:synonym/taxon-id data)
                                  :synonym-name (:synonym/synonym-name data)}})
      (update :activity/data #(store.i/coerce SynonymActivityData %))))

(defmethod activity.i/data-schema created [_]
  SynonymActivityData)

(defmethod activity.i/data-schema deleted [_]
  SynonymActivityData)
