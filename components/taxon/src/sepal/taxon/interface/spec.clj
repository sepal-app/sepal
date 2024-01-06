(ns sepal.taxon.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [next.jdbc.types :as jdbc.types]))

(def id :int)
(def parent-id id)
(def organization-id :int)
(def name [:string {:min 1}])
(def author :string)
(def rank [:enum
           :class
           :family
           :form
           :genus
           :kingdom
           :order
           :phylum
           :section
           :series
           :species
           :subclass
           :subfamily
           :subform
           :subgenus
           :subsection
           :subseries
           :subspecies
           :subtribe
           :subvariety
           :superorder
           :tribe
           :variety])

(def Taxon
  [:map {:closed true}
   [:taxon/id id]
   [:taxon/rank {:decode/db csk/->kebab-case-keyword}
    rank]
   [:taxon/author [:maybe author]]
   [:taxon/name name]
   [:taxon/parent-id [:maybe parent-id]]
   [:taxon/organization-id organization-id]])

(defn coerce-int [v]
  (try
    (cond
      (int? v) v
      (string? v) (Integer/parseInt v)
      (nil? v) v
      :else (int v))
    (catch Exception _
      nil)))

(def CreateTaxon
  [:map {:closed true}
   [:name name]
   [:rank {:decode/db csk/->kebab-case-keyword
           :encode/db (comp jdbc.types/as-other
                            csk/->kebab-case-string)}
    :string]
   [:parent-id {:optional true
                :decode/db coerce-int}
    [:maybe parent-id]]
   [:organization-id {:decode/db coerce-int}
    organization-id]])

(def UpdateTaxon
  (mu/optional-keys
   [:map {:closed true}
    [:name name]
    [:rank {:decode/db csk/->kebab-case-keyword
            :encode/db (comp jdbc.types/as-other
                             csk/->kebab-case-string)}
     rank]
    [:parent-id {:decode/db coerce-int}
     [:maybe id]]]))
