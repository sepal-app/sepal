(ns sepal.taxon.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
            [malli.transform :as mt]
            [malli.util :as mu]))

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
   [:taxon/rank {:decode/db csk/->snake_case_keyword} rank]
   [:taxon/author [:maybe author]]
   [:taxon/parent-id [:maybe parent-id]]
   [:taxon/organization-id organization-id]])

(def CreateTaxon
  [:map
   [:name name]
   [:rank rank]
   [:parent-id {:optional true}] [:maybe parent-id]
   [:organization-id :int]])


(def UpdateTaxon
  (mu/optional-keys
   [:map {:closed true}
    [:name name]
    [:rank {:decode/db csk/->snake_case_string}
     rank ]
    [:parent-id {:decode/db #(when % (parse-long %))}
     [:maybe id] ]]))
