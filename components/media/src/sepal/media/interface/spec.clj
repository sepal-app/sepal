(ns sepal.media.interface.spec
  (:require [camel-snake-kebab.core :as csk]))

(def id pos-int?)
(def s3-bucket :string)
(def s3-key :string)
(def title :string)
(def description :string)
(def size-in-bytes pos-int?)
(def media-type :string)
(def organization-id pos-int?)
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
   [:media/organization-id organization-id]
   [:media/created-at created-at]
   [:media/created-by created-by]])

(def CreateMedia
  [:map {:closed true}
   [:s3-bucket s3-bucket]
   [:s3-key s3-key]
   [:title {:optional true} [:maybe title]]
   [:description {:optional true} [:maybe description]]
   [:size-in-bytes size-in-bytes]
   [:media-type media-type]
   [:organization-id organization-id]
   [:created-at {:optional true} created-at]
   [:created-by created-by]])

(def CreateMediaLink
  [:map {:closed true}
   [:media-id id]
   [:resource-type {:decode/db csk/->kebab-case-keyword
                    :encode/db csk/->kebab-case-string}
    :keyword]
   [:resource-id {:decode/db #(cond (string? %) (parse-long %)
                                    :else %)}
    pos-int?]])

(def MediaLink
  [:map {:closed true}
   [:media-link/media-id id]
   [:media-link/resource-type {:encode/db csk/->kebab-case-string}
    :string]
   [:media-link/resource-id pos-int?]])
