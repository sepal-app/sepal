(ns sepal.observation.interface.spec
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]))

(def id pos-int?)

;; A closed enum, matching note. media_link's open :string predates any need
;; to constrain the type; an open type here would let a typo write a row
;; nothing can ever read back.
;;
;; Accession and taxon are absent on purpose. An observation answers "what did
;; you see, and when", which is a question about a plant standing in a bed. An
;; accession is paperwork and a taxon is a name.
(def resource-type
  [:enum {:decode/store csk/->kebab-case-keyword
          :encode/store csk/->kebab-case-string}
   :material :location])

;; Kept as plain strings rather than enums. Both are curator-editable lookup
;; tables, so a garden's vocabulary is wider than any literal written here,
;; and the composite foreign key is what actually rejects a bad pair.
(def type [:string {:min 1}])
(def value [:string {:min 1}])

;; ISO-8601 dates, stored as text. SQLite has no date type.
(def local-date [:re #"^\d{4}-\d{2}-\d{2}$"])

(def Observation
  [:map {:closed true}
   [:observation/id id]
   [:observation/resource-type resource-type]
   [:observation/resource-id pos-int?]
   [:observation/type type]
   [:observation/value [:maybe value]]
   [:observation/observed-on local-date]
   [:observation/observed-by [:maybe :string]]
   [:observation/next-check-on [:maybe local-date]]
   [:observation/note [:maybe :string]]
   [:observation/created-by [:maybe pos-int?]]
   [:observation/created-at :string]])

(def CreateObservation
  [:map {:closed true}
   [:resource-type resource-type]
   [:resource-id pos-int?]
   [:type type]
   [:value {:optional true} [:maybe value]]
   [:observed-on local-date]
   [:observed-by {:optional true} [:maybe :string]]
   [:next-check-on {:optional true} [:maybe local-date]]
   [:note {:optional true} [:maybe :string]]
   [:created-by {:optional true} [:maybe pos-int?]]])

(def UpdateObservation
  (mu/optional-keys
    [:map {:closed true}
     [:type type]
     [:value [:maybe value]]
     [:observed-on local-date]
     [:observed-by [:maybe :string]]
     [:next-check-on [:maybe local-date]]
     [:note [:maybe :string]]]))
