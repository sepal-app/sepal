(ns sepal.user.interface.spec
  (:require [sepal.validation.interface :refer [email-re]]))

(def id pos-int?)
(def email [:re {:error/message "invalid email"} email-re])
(def password [:string {:min 8}])

(def User
  [:map {:store/columns [:id :email :full_name]}
   [:user/id id]
   [:user/email email]
   [:user/full-name [:maybe :string]]])

(def CreateUser
  [:map
   [:id {:optional true} id]
   [:email email]
   [:password password]])

(def SetPassword
  [:map
   [:password password]])

(def UpdateUser
  [:map
   [:full_name [:maybe :string]]
   [:email email]])
