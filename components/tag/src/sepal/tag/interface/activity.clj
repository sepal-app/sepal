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

;; A tag/created, /updated or /deleted event is about the tag itself, so the
;; tag is the subject and only its name needs recording.
(def TagActivityData
  [:map
   [:name spec/tag-name]
   [:changes {:optional true} [:sequential :string]]])

;; A tag/linked or /unlinked event is about the record that was tagged, so the
;; tagged record is the subject and the tag has to stay in the payload -- it is
;; not recorded anywhere else on the event.
(def TagLinkActivityData
  [:map
   [:tag-id spec/id]
   [:name spec/tag-name]])

(defn create!
  ([db type created-by tag]
   (create! db type created-by tag nil))
  ([db type created-by tag changes]
   (-> (activity.i/create! db {:type type
                               :created-at (Instant/now)
                               :created-by created-by
                               :resource-type :tag
                               :resource-id (:tag/id tag)
                               :data (cond-> {:name (:tag/name tag)}
                                       changes (assoc :changes changes))})
       (update :activity/data #(store.i/coerce TagActivityData %)))))

(defn create-link! [db type created-by tag resource-type resource-id]
  (-> (activity.i/create! db {:type type
                              :created-at (Instant/now)
                              :created-by created-by
                              :resource-type resource-type
                              :resource-id resource-id
                              :data {:tag-id (:tag/id tag)
                                     :name (:tag/name tag)}})
      (update :activity/data #(store.i/coerce TagLinkActivityData %))))

(defmethod activity.i/data-schema created [_] TagActivityData)
(defmethod activity.i/data-schema updated [_] TagActivityData)
(defmethod activity.i/data-schema deleted [_] TagActivityData)
(defmethod activity.i/data-schema linked [_] TagLinkActivityData)
(defmethod activity.i/data-schema unlinked [_] TagLinkActivityData)
