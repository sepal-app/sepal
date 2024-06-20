(ns sepal.taxon.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [next.jdbc.types :as jdbc.types]))

;; (def wfo-plantlist-name-id [:re #"^wfo-\d{10}"])
(def wfo-plantlist-taxon-id [:re #"^wfo-\d{10}-\d{4}-\d{2}"])
(def id pos-int?)
(def organization-id pos-int?)
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

(defn rank->pg-enum [rank]
  (when (some? rank)
    (jdbc.types/as-other rank)))

(def Taxon
  [:map {:closed true}
   [:taxon/id id]
   [:taxon/rank {:decode/db csk/->kebab-case-keyword}
    rank]
   [:taxon/author [:maybe author]]
   [:taxon/name name]
   ;; TODO: If the parent-id is none then use the parent of the taxon that
   ;; wfo-plantlist-name-id references. What about if we want to set the parent
   ;; id to something else? Maybe we just don't allow it as long as it
   ;; references a wfo-plantlist-id. Force the user to create a new org taxon.
   [:taxon/parent-id [:maybe id]]
   [:taxon/organization-id [:maybe organization-id]]
   [:taxon/wfo-taxon-id-2023-12 [:maybe wfo-plantlist-taxon-id]]])

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
   [:author {:optional :true}
    [:maybe author]]
   [:rank {:decode/db csk/->kebab-case-keyword
           :encode/db (comp rank->pg-enum
                            csk/->kebab-case-string)}
    rank]
   [:taxon/wfo-taxon-id-2023-12 {:optional true}
    [:maybe wfo-plantlist-taxon-id]]
   [:parent-id {:optional true
                :decode/db coerce-int}
    [:maybe id]]
   [:organization-id {:decode/db coerce-int}
    organization-id]])

(def UpdateTaxon
  (mu/optional-keys
   [:map {:closed true}
    [:name {:optional true}
     name]
    [:author {:optional true}
     author]
    [:rank {:decode/db csk/->kebab-case-keyword
            :encode/db (comp rank->pg-enum
                             csk/->kebab-case-string)}
     rank]
    [:wfo-taxon-id-2023-12 {:optional true}
     [:maybe wfo-plantlist-taxon-id]]
    [:parent-id {:optional true
                 :decode/db coerce-int}
     [:maybe id]]]))
