(ns sepal.supplier.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]))

(def id pos-int?)
(def name [:string {:min 1}])
(def phone :string)
(def email :string)
(def street-address-1 :string)
(def street-address-2 :string)
(def city :string)
(def postal-code :string)
(def country :string)

(def Supplier
  [:map #_{:closed true}
   [:supplier/id id]
   [:supplier/name name]
   [:supplier/phone [:maybe phone]]
   [:supplier/email [:maybe email]]
   [:supplier/street-address-1 [:maybe street-address-1]]
   [:supplier/street-address-2 [:maybe street-address-2]]
   [:supplier/city [:maybe city]]
   [:supplier/postal-code [:maybe postal-code]]
   [:supplier/country [:maybe country]]])

(def CreateSupplier
  [:map {:closed true}
   [:name name]
   [:phone {:optional true} phone]
   [:email {:optional true} email]
   [:street-address-1 {:optional true} street-address-1]
   [:street-address-2 {:optional true} street-address-2]
   [:city {:optional true} city]
   [:postal-code {:optional true} postal-code]
   [:country {:optional true} country]])

(def UpdateSupplier
  (mu/optional-keys
    [:map {:closed true}
     [:name name]
     [:phone phone]
     [:email email]
     [:street-address-1 street-address-1]
     [:street-address-2 street-address-2]
     [:city city]
     [:postal-code postal-code]
     [:country country]]))
