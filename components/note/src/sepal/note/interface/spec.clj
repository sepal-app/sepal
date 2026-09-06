(ns sepal.note.interface.spec
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]))

(def id pos-int?)
(def body [:string {:min 1}])

;; A closed enum, not the open :string media_link uses. media_link predates any
;; need to constrain the type; an open type here would let a typo write a note
;; nothing can ever read back.
(def resource-type
  [:enum {:decode/store csk/->kebab-case-keyword
          :encode/store csk/->kebab-case-string}
   :accession :material :taxon])

(def Note
  [:map {:closed true}
   [:note/id id]
   [:note/body body]
   [:note/resource-type resource-type]
   [:note/resource-id pos-int?]
   [:note/created-by [:maybe pos-int?]]
   [:note/created-at :string]])

(def CreateNote
  [:map {:closed true}
   [:body body]
   [:resource-type resource-type]
   [:resource-id pos-int?]
   [:created-by {:optional true} [:maybe pos-int?]]])

(def UpdateNote
  (mu/optional-keys
    [:map {:closed true}
     [:body body]]))
