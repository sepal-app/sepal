(ns sepal.media.interface.spec
  (:require [camel-snake-kebab.core :as csk]))

(def id pos-int?)
(def s3-bucket :string)
(def s3-key :string)
(def title :string)
(def description :string)
(def size-in-bytes pos-int?)
(def media-type :string)
(def created-at :time/instant)
(def created-by pos-int?)

(def Media
  [:map {:closed true}
   [:media/id id]
   [:media/s3-bucket s3-bucket]
   [:media/s3-key s3-key]
   [:media/title [:maybe title]]
   [:media/description [:maybe  description]]
   [:media/size-in-bytes size-in-bytes]
   [:media/media-type media-type]
   [:media/created-at created-at]
   [:media/created-by created-by]])

(def CreateMedia
  [:map {:closed true
         :store/result Media}
   [:s3-bucket s3-bucket]
   [:s3-key s3-key]
   [:title {:optional true} [:maybe title]]
   [:description {:optional true} [:maybe description]]
   [:size-in-bytes size-in-bytes]
   [:media-type media-type]
   [:created-at {:optional true} created-at]
   [:created-by created-by]])

(def MediaLink
  [:map {:closed true}
   [:media-link/id id]
   [:media-link/media-id id]
   [:media-link/resource-type {:encode/store csk/->kebab-case-string}
    :string]
   [:media-link/resource-id pos-int?]])

(def CreateMediaLink
  [:map {:closed true
         :store/result MediaLink}
   [:media-id id]
   [:resource-type {:decode/store csk/->kebab-case-keyword
                    :encode/store csk/->kebab-case-string}
    :keyword]
   [:resource-id {:decode/store #(cond (string? %) (parse-long %)
                                       :else %)}
    pos-int?]])
