(ns sepal.media.interface.spec)

(def id :int)
(def s3-bucket :string)
(def s3-key :string)
(def title :string)
(def description :string)
(def size-in-bytes :int)
(def media-type :string)
(def organization-id :int)
(def created-at :time/instant)
(def created-by :int)

(def Media
  [:map {:closed true}
   [:media/id id]
   [:media/s3-bucket s3-bucket]
   [:media/s3-key s3-key]
   [:media/title [:maybe title]]
   [:media/description [:maybe  description]]
   [:media/size-in-bytes size-in-bytes]
   [:media/media-type media-type]
   [:media/organization-id organization-id]
   [:media/created-at created-at]
   [:media/created-by created-by]])
