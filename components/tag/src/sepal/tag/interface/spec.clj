(ns sepal.tag.interface.spec
  (:require [camel-snake-kebab.core :as csk]))

(def id pos-int?)
(def tag-name [:string {:min 1}])
(def description [:maybe :string])
(def resource-type [:enum :accession :material :taxon])

(def Tag
  [:map {:closed true}
   [:tag/id id]
   [:tag/name tag-name]
   [:tag/description description]
   [:tag/created-at :any]
   [:tag/updated-at :any]])

(def CreateTag
  [:map {:closed true}
   [:name tag-name]
   [:description {:optional true} description]])

(def UpdateTag
  [:map {:closed true}
   [:name {:optional true} tag-name]
   [:description {:optional true} description]])

(def TagLink
  [:map {:closed true}
   [:tag-link/id id]
   [:tag-link/tag-id id]
   [:tag-link/resource-type {:decode/store csk/->kebab-case-keyword} :string]
   [:tag-link/resource-id pos-int?]])

(def CreateTagLink
  [:map {:closed true}
   [:tag-id id]
   [:resource-type {:decode/store csk/->kebab-case-keyword
                    :encode/store csk/->kebab-case-string}
    :keyword]
   [:resource-id {:decode/store #(cond (string? %) (parse-long %) :else %)}
    pos-int?]])
