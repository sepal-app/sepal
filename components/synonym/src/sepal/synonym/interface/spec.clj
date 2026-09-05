(ns sepal.synonym.interface.spec
  (:require [malli.util :as mu]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def taxon-id pos-int?)
(def synonym-name [:string {:min 1}])
(def source [:enum "local" "imported"])

(def Synonym
  [:map {:closed true}
   [:synonym/id id]
   [:synonym/taxon-id taxon-id]
   [:synonym/synonym-name synonym-name]
   [:synonym/source source]
   [:synonym/created-by [:maybe pos-int?]]
   [:synonym/created-at :any]])

(def CreateSynonym
  [:map {:closed true}
   [:taxon-id {:decode/store validate.i/coerce-int} taxon-id]
   [:synonym-name synonym-name]
   [:source {:optional true} source]
   [:created-by {:optional true} [:maybe pos-int?]]])

(def UpdateSynonym (mu/optional-keys CreateSynonym))
