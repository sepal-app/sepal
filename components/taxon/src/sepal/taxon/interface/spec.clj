(ns sepal.taxon.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.data.json :as json]
            [malli.util :as mu]
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
           :lusus
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
           :suborder
           :subsection
           :subseries
           :subspecies
           :subtribe
           :subvariety
           :superorder
           :supertribe
           :tribe
           :unranked
           :variety])

(def VernacularName
  [:map {:closed true
         :encode/store #(when % (cske/transform-keys csk/->kebab-case-string %))}
   [:name :string]
   [:language :string]
   [:default {:optional true} :boolean]])

(def Taxon
  [:map {:closed true}
   [:taxon/id id]
   [:taxon/rank {:decode/store csk/->kebab-case-keyword}
    rank]
   [:taxon/author [:maybe author]]
   [:taxon/name name]
   ;; TODO: If the parent-id is none then use the parent of the taxon that
   ;; wfo-plantlist-name-id references. What about if we want to set the parent
   ;; id to something else? Maybe we just don't allow it as long as it
   ;; references a wfo-plantlist-id. Force the user to create a new org taxon.
   [:taxon/parent-id [:maybe id]]
   [:taxon/wfo-taxon-id [:maybe wfo-plantlist-taxon-id]]
   [:taxon/vernacular-names {:decode/store #(let [vn (when % (json/read-str %))]
                                              (mapv (partial cske/transform-keys csk/->kebab-case-keyword) vn))}
    [:* VernacularName]]])

(def CreateTaxon
  [:map {:closed true}
   ;; TODO: allow specifying an id when creating a taxon
   [:name name]
   [:author {:optional :true}
    [:maybe author]]
   [:rank {:decode/store csk/->kebab-case-keyword
           :encode/store csk/->kebab-case-string}
    rank]
   [:taxon/wfo-taxon-id {:optional true}
    [:maybe wfo-plantlist-taxon-id]]
   [:parent-id {:optional true
                :decode/store validate.i/coerce-int}
    [:maybe id]]
   [:vernacular-names {:optional true
                       :default []
                       :encode/store json/write-str}
    [:* VernacularName]]])

(def UpdateTaxon
  (mu/optional-keys
    [:map {:closed true}
     [:name {:optional true}
      name]
     [:author {:optional true}
      author]
     [:rank {:decode/store csk/->kebab-case-keyword
             :encode/store csk/->kebab-case-string}
      rank]
     [:wfo-taxon-id {:optional true}
      [:maybe wfo-plantlist-taxon-id]]
     [:parent-id {:optional true
                  :decode/store validate.i/coerce-int}
      [:maybe id]]
     ;; TODO: I think writing already saves maps and vectors as json
     [:vernacular-names {:encode/store
                         (fn [v]
                           (tap> (str "v: " v))
                           (json/write-str v))}
      [:* VernacularName]]]))
