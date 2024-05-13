(ns sepal.taxon.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [next.jdbc.types :as jdbc.types]))

(def wfo-plantlist-name-id :string)
(def id [:or :int wfo-plantlist-name-id])
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
   [:taxon/parent-id [:maybe parent-id]]
   [:taxon/organization-id [:maybe organization-id]]
   [:taxon/wfo-plantlist-name-id [:maybe wfo-plantlist-name-id]]])

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
   [:rank {:encode/db (comp rank->pg-enum
                            csk/->kebab-case-string)}
    rank]
   [:wfo-plantlist-name-id {:optional true}
    [:maybe wfo-plantlist-name-id]]
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
            :encode/db (comp rank->pg-enum
                             csk/->kebab-case-string)}
     rank]
    [:wfo-plantlist-name-id {:optional true}
     [:maybe wfo-plantlist-name-id]]
    [:parent-id {:decode/db coerce-int}
     [:maybe id]]
    [:organization-id {:decode/db coerce-int}
     [:maybe id]]]))
