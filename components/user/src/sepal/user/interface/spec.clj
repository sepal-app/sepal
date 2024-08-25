(ns sepal.user.interface.spec
  (:require [sepal.validation.interface :refer [email-re]]))

(def id pos-int?)
(def email [:re {:error/message "invalid email"} email-re])
(def password [:string {:min 8}])

(def User
  [:map
   [:user/id id]
   [:user/email email]])

(def CreateUser
  [:map {:store/result User}
   [:id {:optional true} id]
   [:email email]
   [:password {:encode/store (fn [p]
                               [:crypt p [:gen_salt "bf"]])}
    password]])
