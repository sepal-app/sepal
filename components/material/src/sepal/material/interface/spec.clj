(ns sepal.material.interface.spec
  (:refer-clojure :exclude [type])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def accession-id pos-int?)
(def code [:string {:min 1}])
(def location-id pos-int?)
(def memorial [:boolean
               {:decode/store #(and (int? %) (= % 1))
                :encode/store #(if (true? %) 1 0)}])
(def quantity nat-int?)
(def status [:enum :alive :dead :dormant :transferred :other :unknown])
(def type [:enum :plant :seed :vegetative :tissue :other])

(def Material
  [:map {:closed true}
   [:material/id id]
   [:material/code code]
   [:material/accession-id accession-id]
   [:material/location-id location-id]
   [:material/type {:decode/store csk/->kebab-case-keyword
                    :encode/store csk/->kebab-case-string}
    type]
   [:material/status {:decode/store csk/->kebab-case-keyword
                      :encode/store csk/->kebab-case-string}
    status]
   [:material/memorial memorial]
   [:material/quantity quantity]])

(def CreateMaterial
  [:map {:closed true}
   [:code code]
   [:accession-id {:decode/store validate.i/coerce-int}
    accession-id]
   [:location-id {:decode/store validate.i/coerce-int}
    location-id]

   [:type {:decode/store csk/->kebab-case-keyword
           :encode/store csk/->kebab-case-string}
    type]
   [:status {:decode/store csk/->kebab-case-keyword
             :encode/store csk/->kebab-case-string}
    status]
   [:memorial {:optional true} memorial]
   [:quantity {:decode/store validate.i/coerce-int}
    quantity]])

(def UpdateMaterial
  (mu/optional-keys
    [:map {:closed true}
     [:code code]
     [:accession-id {:decode/store validate.i/coerce-int}
      accession-id]
     [:location-id {:decode/store validate.i/coerce-int}
      location-id]
     [:type {:decode/store csk/->kebab-case-keyword
             :encode/store csk/->kebab-case-string}
      type]
     [:status {:decode/store csk/->kebab-case-keyword
               :encode/store csk/->kebab-case-string}
      status]
     [:memorial memorial]
     [:quantity {:decode/store validate.i/coerce-int}
      quantity]]))

(def change-from-location-id [:maybe location-id])
(def change-to-location-id [:maybe location-id])
(def change-quantity int?)
(def change-reason [:maybe :string])
(def change-note [:maybe :string])

(def MaterialChange
  [:map {:closed true}
   [:material-change/id id]
   [:material-change/material-id accession-id]
   [:material-change/from-location-id change-from-location-id]
   [:material-change/to-location-id change-to-location-id]
   [:material-change/quantity change-quantity]
   [:material-change/reason change-reason]
   [:material-change/changed-at :string]
   [:material-change/note change-note]
   [:material-change/created-by [:maybe pos-int?]]
   [:material-change/created-at :string]])

(def CreateMaterialChange
  [:map {:closed true}
   [:material-id {:decode/store validate.i/coerce-int}
    accession-id]
   [:from-location-id {:optional true
                       :decode/store validate.i/coerce-int}
    change-from-location-id]
   [:to-location-id {:optional true
                     :decode/store validate.i/coerce-int}
    change-to-location-id]
   [:quantity {:decode/store validate.i/coerce-int}
    change-quantity]
   [:reason {:optional true} change-reason]
   [:changed-at {:optional true} :string]
   [:note {:optional true} change-note]
   [:created-by {:optional true
                 :decode/store validate.i/coerce-int}
    [:maybe pos-int?]]])
