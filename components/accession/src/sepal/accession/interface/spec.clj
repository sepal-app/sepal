(ns sepal.accession.interface.spec
  (:require [malli.util :as mu]))

(def id :int)
(def taxon-id :int)
(def organization-id :int)
(def code [:string {:min 1}])

(def Accession
  [:map #_{:closed true}
   [:accession/id id]
   [:accession/code code]
   [:accession/taxon-id taxon-id]
   [:accession/organization-id organization-id]])

(defn coerce-int [v]
  (cond
    (int? v) v
    (string? v) (Integer/parseInt v)
    (nil? v) v
    :else (int v)))

(def CreateAccession
  [:map {:closed true
         :store/result Accession}
   [:code code]
   [:taxon-id {:optional true
               :decode/store coerce-int}
    [:maybe taxon-id]]
   [:organization-id {:decode/store coerce-int}
    organization-id]])

(def UpdateAccession
  (mu/optional-keys
    [:map {:closed true
           :store/result Accession}
     [:code code]
     [:taxon-id {:decode/store coerce-int}
      [:maybe taxon-id]]]))
