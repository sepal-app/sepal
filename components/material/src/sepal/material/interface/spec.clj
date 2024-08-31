(ns sepal.material.interface.spec
  (:refer-clojure :exclude [name type])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [sepal.database.interface :as db.i]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def accession-id pos-int?)
(def code [:string {:min 1}])
(def location-id pos-int?)
(def memorial :boolean)
(def organization-id pos-int?)
(def quantity pos-int?)
(def status [:enum :alive :dead])
(def type [:enum :plant :seed :vegetative :tissue :other :none])

(def Material
  [:map {:closed true}
   [:material/id id]
   [:material/code code]
   [:material/accession-id accession-id]
   [:material/location-id location-id]
   [:material/organization-id organization-id]
   [:material/type {:decode/store csk/->kebab-case-keyword} type]
   [:material/status {:decode/store csk/->kebab-case-keyword} status]
   [:material/memorial memorial]
   [:material/quantity quantity]])

(def CreateMaterial
  [:map {:closed true
         :store/result Material}
   [:code code]
   [:accession-id {:decode/store validate.i/coerce-int}
    accession-id]
   [:location-id {:decode/store validate.i/coerce-int}
    location-id]
   [:organization-id {:decode/store validate.i/coerce-int}
    organization-id]
   [:type {:decode/store csk/->kebab-case-keyword
           :encode/store (comp db.i/->pg-enum
                               csk/->kebab-case-string)}
    type]
   [:status {:decode/store csk/->kebab-case-keyword
             :encode/store (comp db.i/->pg-enum
                                 csk/->kebab-case-string)}
    status]
   [:memorial {:optional true} memorial]
   [:quantity {:decode/store validate.i/coerce-int}
    quantity]])

(def UpdateMaterial
  (mu/optional-keys
    [:map {:closed true
           :store/result Material}
     [:code code]
     [:accession-id {:decode/store validate.i/coerce-int}
      accession-id]
     [:location-id {:decode/store validate.i/coerce-int}
      location-id]
     [:type {:decode/store csk/->kebab-case-keyword
             :encode/store (comp db.i/->pg-enum
                                 csk/->kebab-case-string)}
      type]
     [:memorial memorial]
     [:quantity {:decode/store validate.i/coerce-int}
      quantity]]))
