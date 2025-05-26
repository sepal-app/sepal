(ns sepal.accession.interface.spec
  (:require [malli.util :as mu]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def taxon-id pos-int?)
(def code [:string {:min 1}])

(def Accession
  [:map #_{:closed true}
   [:accession/id id]
   [:accession/code code]
   [:accession/taxon-id taxon-id]])

(def CreateAccession
  [:map {:closed true}
   [:code code]
   [:taxon-id {:decode/store validate.i/coerce-int}
    taxon-id]])

(def UpdateAccession
  (mu/optional-keys
    [:map {:closed true}
     [:code code]
     [:taxon-id {:decode/store validate.i/coerce-int}
      [:maybe taxon-id]]]))
