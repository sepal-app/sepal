(ns sepal.accession.interface.spec
  (:require [malli.util :as mu]
            [sepal.i18n.interface :refer [N_]]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def taxon-id pos-int?)
(def code [:string {:min 1}])
(def private [:boolean
              {:decode/store #(and (int? %) (= % 1))
               :encode/store #(if (true? %) 1 0)}])
(def supplier-contact-id pos-int?)
(def intended-location-id pos-int?)

(defn- name-encoder [v]
  (when v (name v)))

(defn- keyword-encoder [v]
  (when v (keyword v)))

(def id-qualifier [:enum {:decode/store keyword
                          :encode/store name-encoder
                          :decode/params keyword-encoder}
                   :aff
                   :cf
                   :forsan
                   :incorrect
                   :near
                   :questionable])

(def id-qualifier-rank [:enum {:decode/store keyword
                               :encode/store name-encoder
                               :decode/params keyword-encoder}
                        :below_family
                        :family
                        :genus
                        :species
                        :first_infraspecific_epithet
                        :second_infraspecific_epithet
                        :cultivar])

(def id-qualifier-rank-labels
  "Display names for id-qualifier-rank, translated where they are rendered."
  {:below_family (N_ "Below family")
   :family (N_ "Family")
   :genus (N_ "Genus")
   :species (N_ "Species")
   :first_infraspecific_epithet (N_ "First infraspecific epithet")
   :second_infraspecific_epithet (N_ "Second infraspecific epithet")
   :cultivar (N_ "Cultivar")})

(def provenance-type [:enum {:decode/store keyword
                             :encode/store name-encoder
                             :decode/params keyword-encoder}
                      :wild
                      :cultivated
                      :not_wild
                      :purchase
                      :insufficient_data])

(def provenance-type-labels
  "Display names for provenance-type, translated where they are rendered."
  {:wild (N_ "Wild")
   :cultivated (N_ "Cultivated")
   :not_wild (N_ "Not wild")
   :purchase (N_ "Purchase")
   :insufficient_data (N_ "Insufficient data")})

(def wild-provenance-status [:enum {:decode/store keyword
                                    :encode/store name-encoder
                                    :decode/params keyword-encoder}
                             :wild_native
                             :wild_non_native
                             :cultivated_native
                             :cultivated
                             :not_wild
                             :purchase
                             :insufficient_data])

(def wild-provenance-status-labels
  "Display names for wild-provenance-status, translated where they are rendered."
  {:wild_native (N_ "Wild native")
   :wild_non_native (N_ "Wild non native")
   :cultivated_native (N_ "Cultivated native")
   :cultivated (N_ "Cultivated")
   :not_wild (N_ "Not wild")
   :purchase (N_ "Purchase")
   :insufficient_data (N_ "Insufficient data")})

;; The form material arrived in. Mirrors the accession_received_type rows; a
;; test holds the two equal.
(def received-type [:enum {:decode/store keyword
                           :encode/store name-encoder
                           :decode/params keyword-encoder}
                    :air_layer
                    :balled_and_burlapped
                    :bare_root_plant
                    :bud_cutting
                    :budded
                    :bulb
                    :bulbil
                    :clump
                    :corm
                    :division
                    :graft
                    :layer
                    :plant
                    :pseudobulb
                    :rhizome
                    :root
                    :root_cutting
                    :root_sucker
                    :rooted_cutting
                    :scion
                    :seed
                    :seedling
                    :spore
                    :sporeling
                    :tuber
                    :unknown
                    :unrooted_cutting
                    :vegetative_spreading])

(def received-type-labels
  "Display names for received-type, translated where they are rendered."
  {:air_layer (N_ "Air layer")
   :balled_and_burlapped (N_ "Balled and burlapped")
   :bare_root_plant (N_ "Bare root plant")
   :bud_cutting (N_ "Bud cutting")
   :budded (N_ "Budded")
   :bulb (N_ "Bulb")
   :bulbil (N_ "Bulbil")
   :clump (N_ "Clump")
   :corm (N_ "Corm")
   :division (N_ "Division")
   :graft (N_ "Graft")
   :layer (N_ "Layer")
   :plant (N_ "Plant")
   :pseudobulb (N_ "Pseudobulb")
   :rhizome (N_ "Rhizome")
   :root (N_ "Root")
   :root_cutting (N_ "Root cutting")
   :root_sucker (N_ "Root sucker")
   :rooted_cutting (N_ "Rooted cutting")
   :scion (N_ "Scion")
   :seed (N_ "Seed")
   :seedling (N_ "Seedling")
   :spore (N_ "Spore")
   :sporeling (N_ "Sporeling")
   :tuber (N_ "Tuber")
   :unknown (N_ "Unknown")
   :unrooted_cutting (N_ "Unrooted cutting")
   :vegetative_spreading (N_ "Vegetative spreading")})

;; How many propagules arrived. Not material.quantity, which is how many plants
;; exist now. Zero is legitimate: an accession recorded with nothing received.
(def quantity-received [:int {:min 0}])

(def Accession
  [:map #_{:closed true}
   [:accession/id id]
   [:accession/code code]
   [:accession/taxon-id taxon-id]
   [:accession/private private]
   [:accession/id-qualifier [:maybe id-qualifier]]
   [:accession/id-qualifier-rank [:maybe id-qualifier-rank]]
   [:accession/provenance-type [:maybe provenance-type]]
   [:accession/wild-provenance-status [:maybe wild-provenance-status]]
   [:accession/supplier-contact-id [:maybe supplier-contact-id]]
   [:accession/intended-location-id [:maybe intended-location-id]]
   [:accession/received-type [:maybe received-type]]
   [:accession/quantity-received [:maybe quantity-received]]
   [:accession/date-received [:maybe :string]]
   [:accession/date-accessioned [:maybe :string]]
   ;; The propagation that produced this accession, for seed taken from a
   ;; plant here. Null for an accession that arrived.
   [:accession/propagation-id [:maybe id]]])

(def CreateAccession
  [:map {:closed true}
   [:code code]
   [:taxon-id {:decode/store validate.i/coerce-int}
    taxon-id]
   [:private {:optional true} private]
   [:id-qualifier {:optional true} [:maybe id-qualifier]]
   [:id-qualifier-rank {:optional true} [:maybe id-qualifier-rank]]
   [:provenance-type {:optional true} [:maybe provenance-type]]
   [:wild-provenance-status {:optional true} [:maybe wild-provenance-status]]
   [:supplier-contact-id {:optional true} [:maybe supplier-contact-id]]
   [:intended-location-id {:optional true} [:maybe intended-location-id]]
   [:received-type {:optional true} [:maybe received-type]]
   [:quantity-received {:optional true :decode/store validate.i/coerce-int}
    [:maybe quantity-received]]
   [:date-received {:optional true} [:maybe :string]]
   [:date-accessioned {:optional true} [:maybe :string]]
   [:propagation-id {:optional true
                     :decode/store validate.i/coerce-int}
    ;; Generated data has no propagation to point at, and a random foreign key
    ;; fails the insert.
    [:maybe {:gen/return nil} id]]])

(def UpdateAccession
  (mu/optional-keys
    [:map {:closed true}
     [:code code]
     [:taxon-id {:optional true :decode/store validate.i/coerce-int} taxon-id]
     [:private {:optional true} private]
     [:id-qualifier {:optional true} [:maybe id-qualifier]]
     [:id-qualifier-rank {:optional true} [:maybe id-qualifier-rank]]
     [:provenance-type {:optional true} [:maybe provenance-type]]
     [:wild-provenance-status {:optional true} [:maybe wild-provenance-status]]
     [:supplier-contact-id {:optional true} [:maybe supplier-contact-id]]
     [:intended-location-id {:optional true} [:maybe intended-location-id]]
     [:received-type {:optional true} [:maybe received-type]]
     [:quantity-received {:optional true :decode/store validate.i/coerce-int}
      [:maybe quantity-received]]
     [:date-received {:optional true} [:maybe :string]]
     [:date-accessioned {:optional true} [:maybe :string]]
     [:propagation-id {:optional true
                       :decode/store validate.i/coerce-int}
      [:maybe {:gen/return nil} id]]]))
