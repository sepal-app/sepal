(ns sepal.taxon.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
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
   [:taxon/rank {:decode/db csk/->snake_case_keyword}
    rank]
   [:taxon/author [:maybe author]]
   [:taxon/parent-id [:maybe parent-id]]
   [:taxon/organization-id organization-id]])


(defn coerce-int [v]
  (cond
    (int? v) v
    (string? v) (Integer/parseInt v)
    (nil? v) v
    :else (int v )))

(def CreateTaxon
  [:map {:closed true}
   [:name name]
   ;; TODO: How can we validate that the rank is a valid rank keyword
   ;; and also use jdbc.tpes/as-other on it?  Maybe using :enter/:leave decoders.
   [:rank {:decode/db
           ;; #(when (seq %) (jdbc.types/as-other %))
            csk/->snake_case_string}
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
    [:rank {:decode/db csk/->snake_case_string}
     rank ]
    [:parent-id {:decode/db coerce-int}
     [:maybe id] ]]))
