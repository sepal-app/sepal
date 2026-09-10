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
;; A note's subject is the record it hangs on, not the note. That is what a
;; reader of an accession's history is asking for, and it is why a note event
;; appeared on no panel until the subject moved into the columns: the payload
;; named the parent generically, and the old query looked for `accession-id`.
(def NoteActivityData
  [:map
   [:note-id spec/id]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type (:note/resource-type data)
                           :resource-id (:note/resource-id data)
                           :data {:note-id (:note/id data)}})
      (update :activity/data #(store.i/coerce NoteActivityData %))))

(defmethod activity.i/data-schema created [_]
  NoteActivityData)

(defmethod activity.i/data-schema updated [_]
  NoteActivityData)

(defmethod activity.i/data-schema deleted [_]
  NoteActivityData)
