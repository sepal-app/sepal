(ns sepal.tag.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.store.interface :as store.i]
            [sepal.tag.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :tag/created)
(def updated :tag/updated)
(def deleted :tag/deleted)
(def linked :tag/linked)
(def unlinked :tag/unlinked)

(def TagActivityData
  [:map
   [:tag-id spec/id]
   [:name spec/tag-name]])

(def TagLinkActivityData
  [:map
   [:tag-id spec/id]
   [:name spec/tag-name]
   [:resource-type :string]
   [:resource-id pos-int?]])

(defn create! [db type created-by tag]
  (-> (activity.i/create! db {:type type
                              :created-at (Instant/now)
                              :created-by created-by
                              :data {:tag-id (:tag/id tag)
                                     :name (:tag/name tag)}})
      (update :activity/data #(store.i/coerce TagActivityData %))))

(defn create-link! [db type created-by tag resource-type resource-id]
  (-> (activity.i/create! db {:type type
                              :created-at (Instant/now)
                              :created-by created-by
                              :data {:tag-id (:tag/id tag)
                                     :name (:tag/name tag)
                                     :resource-type (name resource-type)
                                     :resource-id resource-id}})
      (update :activity/data #(store.i/coerce TagLinkActivityData %))))

(defmethod activity.i/data-schema created [_] TagActivityData)
(defmethod activity.i/data-schema updated [_] TagActivityData)
(defmethod activity.i/data-schema deleted [_] TagActivityData)
(defmethod activity.i/data-schema linked [_] TagLinkActivityData)
(defmethod activity.i/data-schema unlinked [_] TagLinkActivityData)
