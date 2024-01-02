(ns sepal.material.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]))

(def id :int)
(def organization-id :int)
(def accession-id :int)
(def location-id :int)
(def code [:string {:min 2}])

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
   [:material/organization-id organization-id]])

(def CreateMaterial
  [:map {:closed true}
   [:code code]
   [:accession-id {:decode/db coerce-int}
    accession-id]
   [:location-id {:decode/db coerce-int}
    location-id]
   [:organization-id {:decode/db coerce-int}
    organization-id]])

(def UpdateMaterial
  (mu/optional-keys
   [:map {:closed true}
    [:code code]
    [:accession-id {:decode/db coerce-int}
     accession-id]
    [:location-id {:decode/db coerce-int}
     location-id]]))
