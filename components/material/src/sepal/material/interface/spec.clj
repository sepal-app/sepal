(ns sepal.material.interface.spec
  (:refer-clojure :exclude [name type])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [sepal.database.interface :as db.i]))

(def id pos-int?)
(def organization-id pos-int?)
(def accession-id pos-int?)
(def location-id pos-int?)
(def code [:string {:min 1}])
(def type [:enum [:plant :seed :vegetative :tissue :other :none]])
(def memorial :boolean)
(def quantity pos-int?)

;; (def propagations pos-int?)

(defn coerce-int [v]
  (cond
    (int? v) v
    (string? v) (Integer/parseInt v)
    (nil? v) v
    :else (int v)))

(def Material
  [:map {:closed true}
   [:material/id id]
   [:material/code code]
   [:material/accession-id accession-id]
   [:material/location-id location-id]
   [:material/organization-id organization-id]
   [:material/type {:decode/store csk/->kebab-case-keyword} type]
   [:material/memorial memorial]
   [:material/quantity quantity]])

(def CreateMaterial
  [:map {:closed true
         :store/result Material}
   [:code code]
   [:accession-id {:decode/store coerce-int}
    accession-id]
   [:location-id {:decode/store coerce-int}
    location-id]
   [:organization-id {:decode/store coerce-int}
    organization-id]
   [:type {:decode/store csk/->kebab-case-keyword
           :encode/store (comp db.i/->pg-enum
                               csk/->kebab-case-string)}
    type]
   [:memorial memorial]
   [:quantity {:decode/store coerce-int}
    quantity]])

(def UpdateMaterial
  (mu/optional-keys
    [:map {:closed true
           :store/result Material}
     [:code code]
     [:accession-id {:decode/store coerce-int}
      accession-id]
     [:location-id {:decode/store coerce-int}
      location-id]
     [:type {:decode/store csk/->kebab-case-keyword
             :encode/store (comp db.i/->pg-enum
                                 csk/->kebab-case-string)}
      type]
     [:memorial memorial]
     [:quantity {:decode/store coerce-int}
      quantity]]))
