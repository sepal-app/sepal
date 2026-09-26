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

;; The link keys name the record the media was linked to or unlinked from, as
;; it read at the time, so the event still reads once that record is gone. An
;; upload made from a record's Media tab carries them on its `created` event
;; rather than recording a separate `linked` one.
(def MediaActivityData
  [:map
   [:s3-key spec/s3-key]
   [:media-type spec/media-type]
   [:link-resource-type {:optional true} :string]
   [:link-resource-id {:optional true} pos-int?]
   [:link-text {:optional true} [:maybe :string]]])

(def MediaLinkActivityData
  [:map
   [:s3-key spec/s3-key]
   [:link-resource-type :string]
   [:link-resource-id pos-int?]
   [:link-text [:maybe :string]]])

(defn create!
  "Record `type` for `media`. `link` and `link-text` name the record an upload
  was linked to, when it was."
  [db type created-by media & {:keys [link link-text]}]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :resource-type :media
                           :resource-id (:media/id media)
                           :data (cond-> {:s3-key (:media/s3-key media)
                                          :media-type (:media/media-type media)}
                                   link (assoc :link-resource-type (:media-link/resource-type link)
                                               :link-resource-id (:media-link/resource-id link)
                                               :link-text link-text))})
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
