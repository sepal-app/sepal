(ns sepal.accession.interface.spec
  (:require [malli.util :as mu]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def taxon-id pos-int?)
(def code [:string {:min 1}])
(def private [:boolean
              {:decode/store #(and (int? %) (= % 1))
               :encode/store #(if (true? %) 1 0)}])
(def supplier-contact-id pos-int?)

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

(def provenance-type [:enum {:decode/store keyword
                             :encode/store name-encoder
                             :decode/params keyword-encoder}
                      :wild
                      :cultivated
                      :not_wild
                      :purchase
                      :insufficient_data])

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
   [:accession/date-received [:maybe :string]]
   [:accession/date-accessioned [:maybe :string]]])

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
   [:date-received {:optional true} [:maybe :string]]
   [:date-accessioned {:optional true} [:maybe :string]]])

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
     [:date-received {:optional true} [:maybe :string]]
     [:date-accessioned {:optional true} [:maybe :string]]]))
