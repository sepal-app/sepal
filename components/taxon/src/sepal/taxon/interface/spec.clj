(ns sepal.taxon.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [sepal.database.interface :as db.i]
            [sepal.validation.interface :as validate.i]))

;; (def wfo-plantlist-name-id [:re #"^wfo-\d{10}"])
(def wfo-plantlist-taxon-id [:re #"^wfo-\d{10}-\d{4}-\d{2}"])
(def id pos-int?)
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
           :prole
           :section
           :series
           :species
           :subclass
           :subfamily
           :subform
           :subgenus
           :subkingdom
           :subsection
           :subseries
           :subspecies
           :subtribe
           :subvariety
           :superorder
           :tribe
           :variety])
(def read-only [:boolean])

(def Taxon
  [:map {:closed true}
   [:taxon/id id]
   [:taxon/rank {:decode/store csk/->kebab-case-keyword}
    rank]
   [:taxon/author [:maybe author]]
   [:taxon/name name]
   [:taxon/read-only read-only]
   ;; TODO: If the parent-id is none then use the parent of the taxon that
   ;; wfo-plantlist-name-id references. What about if we want to set the parent
   ;; id to something else? Maybe we just don't allow it as long as it
   ;; references a wfo-plantlist-id. Force the user to create a new org taxon.
   [:taxon/parent-id [:maybe id]]
   [:taxon/wfo-taxon-id-2023-12 [:maybe wfo-plantlist-taxon-id]]])

(def CreateTaxon
  [:map {:closed true
         :store/result Taxon}
   ;; TODO: allow specifying an id when creating a taxon
   [:name name]
   [:author {:optional :true}
    [:maybe author]]
   [:rank {:decode/store csk/->kebab-case-keyword
           :encode/store (comp db.i/->pg-enum
                               csk/->kebab-case-string)}
    rank]
   [:taxon/wfo-taxon-id-2023-12 {:optional true}
    [:maybe wfo-plantlist-taxon-id]]
   [:parent-id {:optional true
                :decode/store validate.i/coerce-int}
    [:maybe id]]])

(def UpdateTaxon
  (mu/optional-keys
    [:map {:closed true
           :store/result Taxon}
     [:name {:optional true}
      name]
     [:author {:optional true}
      author]
     [:rank {:decode/store csk/->kebab-case-keyword
             :encode/store (comp db.i/->pg-enum
                                 csk/->kebab-case-string)}
      rank]
     [:wfo-taxon-id-2023-12 {:optional true}
      [:maybe wfo-plantlist-taxon-id]]
     [:parent-id {:optional true
                  :decode/store validate.i/coerce-int}
      [:maybe id]]]))
