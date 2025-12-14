(ns sepal.user.interface.spec
  (:require [sepal.validation.interface :refer [email-re]]))

(def id pos-int?)
(def email [:re {:error/message "invalid email"} email-re])
(def password [:string {:min 8}])

(defn- name-encoder [v]
  (when v (name v)))

(def role [:enum {:decode/store keyword
                  :encode/store name-encoder}
           :admin :editor :reader])

(def User
  ;; Be explicit about which columns to select to avoid selecting the password by default
  [:map {:store/columns [:id :email :full_name :role]}
   [:user/id id]
   [:user/email email]
   [:user/full-name [:maybe :string]]
   [:user/role role]])

(def CreateUser
  [:map
   [:id {:optional true} id]
   [:email email]
   [:password password]
   [:role role]])

(def SetPassword
  [:map
   [:password password]])

(def UpdateUser
  [:map
   [:full_name [:maybe :string]]
   [:email email]])
