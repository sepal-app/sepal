(ns sepal.media.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.media.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :media/created)
(def deleted :media/deleted)

(def MediaActivityData
  [:map
   [:s3-key spec/s3-key]
   [:media-type spec/media-type]])

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
