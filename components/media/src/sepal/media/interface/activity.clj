(ns sepal.media.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.media.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :media/created)
(def deleted :media/deleted)
(def updated :media/updated)
(def linked :media/linked)
(def unlinked :media/unlinked)

(def MediaActivityData
  [:map
   [:s3-key spec/s3-key]
   [:media-type spec/media-type]])

;; The record the media was linked to or unlinked from, named as it read at
;; the time, so the event still reads once that record is gone.
(def MediaLinkActivityData
  [:map
   [:s3-key spec/s3-key]
   [:link-resource-type :string]
   [:link-resource-id pos-int?]
   [:link-text [:maybe :string]]])

(defn create! [db type created-by media]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type :media
                           :resource-id (:media/id media)
                           :data {:s3-key (:media/s3-key media)
                                  :media-type (:media/media-type media)}})
      (update :activity/data #(store.i/coerce MediaActivityData %))))

(defmethod activity.i/data-schema created [_]
  MediaActivityData)

(defmethod activity.i/data-schema deleted [_]
  MediaActivityData)

(defmethod activity.i/data-schema updated [_]
  MediaActivityData)

(defn create-link!
  "Record `linked` or `unlinked` for `media`, against `link` as it stood."
  [db type created-by media link link-text]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type :media
                           :resource-id (:media/id media)
                           :data {:s3-key (:media/s3-key media)
                                  :link-resource-type (:media-link/resource-type link)
                                  :link-resource-id (:media-link/resource-id link)
                                  :link-text link-text}})
      (update :activity/data #(store.i/coerce MediaLinkActivityData %))))

(defmethod activity.i/data-schema linked [_]
  MediaLinkActivityData)

(defmethod activity.i/data-schema unlinked [_]
  MediaLinkActivityData)
