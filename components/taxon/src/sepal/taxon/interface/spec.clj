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
           :aggregate
           :class
           :convariety
           :cultivar
           :family
           :form
           :genus
           :grex
           :group
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
           :subphylum
           :subsection
           :subseries
           :subspecies
           :subtribe
           :subvariety
           :superclass
           :superfamily
           :superorder
           :supertribe
           :tribe
           :unranked
           :variety])

(def VernacularName
  [:map {:closed true
         :encode/store #(when % (cske/transform-keys csk/->kebab-case-string %))}
   [:name :string]
   ;; Optional: a name whose language nobody recorded is still the name, and
   ;; the taxon form already declares it [:maybe :string]. Only the browser
   ;; always sending "" for a blank field hid the disagreement.
   [:language {:optional true} [:maybe :string]]
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
   [:taxon/distribution [:maybe :string]]
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
   [:wfo-taxon-id {:optional true}
    [:maybe wfo-plantlist-taxon-id]]
   [:parent-id {:optional true
                :decode/store validate.i/coerce-int}
    [:maybe id]]
   [:distribution {:optional true} [:maybe :string]]
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
     [:distribution {:optional true} [:maybe :string]]
     ;; TODO: I think writing already saves maps and vectors as json
     [:vernacular-names {:encode/store json/write-str}
      [:* VernacularName]]]))

(def parentage-role
  "Which side of the cross a parent was.

   `unknown` is the default and the common case: convention writes the seed
   parent first and reciprocal crosses genuinely differ, but for most garden
   records nobody recorded which way it went. A row saying so is honest where
   a guess would not be.

   A closed set, so it is a check constraint on `taxon_parentage` rather than
   a lookup table. Contrast `contact_type`, a vocabulary a garden outgrows."
  [:enum {:decode/store csk/->kebab-case-keyword
          :encode/store csk/->kebab-case-string}
   :seed :pollen :unknown])

(def Parentage
  "One parent of one cross, as stored."
  [:map {:closed true}
   [:parentage/id id]
   [:parentage/taxon-id id]
   [:parentage/parent-taxon-id id]
   [:parentage/role parentage-role]
   [:parentage/position :int]
   [:parentage/created-by [:maybe pos-int?]]
   [:parentage/created-at :any]])

(def CreateParentage
  "One parent as a caller supplies it. `position` is assigned by the writer
   from the order of the collection, so a caller does not carry it."
  [:map {:closed true}
   [:parent-taxon-id {:decode/store validate.i/coerce-int} id]
   [:role {:optional true} parentage-role]])
