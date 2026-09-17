(ns sepal.contact.interface.spec
  (:refer-clojure :exclude [name type])
  (:require [malli.util :as mu]
            [sepal.validation.interface :refer [email-re]]))

(defn- name-encoder [v]
  (when v (clojure.core/name v)))

(defn- keyword-encoder [v]
  (when v (keyword v)))

(def type
  "What kind of party a contact is, over Bauble's source_type vocabulary.
   BG and Research/FieldStation are spelled out because Bauble's codes were
   abbreviations for a fixed-width UI, and a slash in an enum value invites
   escaping problems."
  [:enum {:decode/store keyword
          :encode/store name-encoder
          :decode/params keyword-encoder}
   :expedition
   :staff
   :commercial
   :gene_bank
   :university_department
   :individual
   :botanic_garden
   :club
   :other
   :research_station
   :municipal_department
   :unknown])

(def id pos-int?)
(def name [:string {:min 2}])
(def email [:re {:error/message "invalid email"} email-re])
(def address
  "The single address line every contact had before address1/address2/city.

   Kept because a one-line address cannot be split into street and city by any
   rule that is right on real data. Nothing writes it now; the form shows it
   read-only where a contact still carries one."
  :string)
(def address1 :string)
(def address2 :string)
(def city :string)
(def province :string)
(def postal-code :string)
(def country :string)
(def phone :string)
(def business :string)
(def notes :string)

(def Contact
  [:map {:closed true}
   [:contact/id id]
   [:contact/name name]
   [:contact/email [:maybe email]]
   [:contact/address [:maybe address]]
   [:contact/address1 [:maybe address1]]
   [:contact/address2 [:maybe address2]]
   [:contact/city [:maybe city]]
   [:contact/province [:maybe province]]
   [:contact/postal-code [:maybe postal-code]]
   [:contact/country [:maybe country]]
   [:contact/phone [:maybe phone]]
   [:contact/business [:maybe business]]
   [:contact/type [:maybe type]]
   [:contact/notes [:maybe notes]]])

(def CreateContact
  [:map {:closed true}
   ;; [:id id]
   [:name name]
   [:email {:optional true} [:maybe email]]
   [:address {:optional true} [:maybe address]]
   [:address1 {:optional true} [:maybe address1]]
   [:address2 {:optional true} [:maybe address2]]
   [:city {:optional true} [:maybe city]]
   [:province {:optional true} [:maybe province]]
   [:postal-code {:optional true} [:maybe postal-code]]
   [:country {:optional true} [:maybe country]]
   [:phone {:optional true} [:maybe phone]]
   [:business {:optional true} [:maybe business]]
   [:type {:optional true} [:maybe type]]
   [:notes {:optional true} [:maybe notes]]])

(def UpdateContact
  (mu/optional-keys
    [:map {:closed true}
     [:name name]
     [:email {:optional true} [:maybe email]]
     [:address {:optional true} [:maybe address]]
     [:address1 {:optional true} [:maybe address1]]
     [:address2 {:optional true} [:maybe address2]]
     [:city {:optional true} [:maybe city]]
     [:province {:optional true} [:maybe province]]
     [:postal-code {:optional true} [:maybe postal-code]]
     [:country {:optional true} [:maybe country]]
     [:phone {:optional true} [:maybe phone]]
     [:business {:optional true} [:maybe business]]
     [:type {:optional true} [:maybe type]]
     [:notes {:optional true} [:maybe notes]]]))
