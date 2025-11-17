(ns sepal.contact.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]
            [sepal.validation.interface :refer [email-re]]))

(def id pos-int?)
(def name [:string {:min 2}])
(def email [:re {:error/message "invalid email"} email-re])
(def address :string)
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
   [:contact/province [:maybe province]]
   [:contact/postal-code [:maybe postal-code]]
   [:contact/country [:maybe country]]
   [:contact/phone [:maybe phone]]
   [:contact/business [:maybe business]]
   [:contact/notes [:maybe notes]]])

(def CreateContact
  [:map {:closed true}
   ;; [:id id]
   [:name name]
   [:email {:optional true} [:maybe email]]
   [:address {:optional true} [:maybe address]]
   [:province {:optional true} [:maybe province]]
   [:postal-code {:optional true} [:maybe postal-code]]
   [:country {:optional true} [:maybe country]]
   [:phone {:optional true} [:maybe phone]]
   [:business {:optional true} [:maybe business]]
   [:notes {:optional true} [:maybe notes]]])

(def UpdateContact
  (mu/optional-keys
    [:map {:closed true}
     [:name name]
     [:email {:optional true} [:maybe email]]
     [:address {:optional true} [:maybe address]]
     [:province {:optional true} [:maybe province]]
     [:postal-code {:optional true} [:maybe postal-code]]
     [:country {:optional true} [:maybe country]]
     [:phone {:optional true} [:maybe phone]]
     [:business {:optional true} [:maybe business]]
     [:notes {:optional true} [:maybe notes]]]))
