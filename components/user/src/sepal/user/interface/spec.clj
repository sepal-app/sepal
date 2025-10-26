(ns sepal.user.interface.spec
  (:require [sepal.validation.interface :refer [email-re]]))

(def id pos-int?)
(def email [:re {:error/message "invalid email"} email-re])
(def password [:string {:min 8}])

(def User
  [:map {:store/columns [:id :email]}
   [:user/id id]
   [:user/email email]])

(def CreateUser
  [:map
   [:id {:optional true} id]
   [:email email]
   [:password password]])

(def SetPassword
  [:map
   [:password password]])
