(ns sepal.note.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.note.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :note/created)
(def deleted :note/deleted)
(def updated :note/updated)

;; The body is deliberately absent. An activity feed is not a second copy of
;; the note's text, and 025's changed-field list is the right home for "the
;; body changed".
(def NoteActivityData
  [:map
   [:note-id spec/id]
   [:resource-type spec/resource-type]
   [:resource-id pos-int?]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :data {:note-id (:note/id data)
                                  :resource-type (:note/resource-type data)
                                  :resource-id (:note/resource-id data)}})
      (update :activity/data #(store.i/coerce NoteActivityData %))))

(defmethod activity.i/data-schema created [_]
  NoteActivityData)

(defmethod activity.i/data-schema updated [_]
  NoteActivityData)

(defmethod activity.i/data-schema deleted [_]
  NoteActivityData)
