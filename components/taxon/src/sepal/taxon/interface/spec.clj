(ns sepal.taxon.interface.spec)

(def id :int)
(def parent_id id)
(def name [:string {:min 1}])
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
  [:map
   [:id id]
   [:rank rank]
   [:parent_id {:optional true} parent_id]])

(def CreateTaxon
  [:map
   [:name name]
   [:rank rank]
   [:parent_id {:optional true}]])
