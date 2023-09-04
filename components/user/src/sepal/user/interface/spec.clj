(ns sepal.user.interface.spec
  (:require [sepal.validation.interface :refer [email-re]]))

(def id :int)
(def email [:re email-re])
(def password [:string {:min 8}])
(def created-at :int)

(def CreateUser
  [:map
   [:email email]
   [:password password]])

(def User
  [:map
   [:user/id id]
   [:user/email email]
   [:user/created-at email]])
