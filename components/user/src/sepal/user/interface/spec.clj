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

(def status [:enum {:decode/store keyword
                    :encode/store name-encoder}
             :invited :active :archived])

(def User
  ;; Be explicit about which columns to select to avoid selecting the password by default
  [:map {:store/columns [:id :email :full_name :role :status]}
   [:user/id id]
   [:user/email email]
   [:user/full-name {:optional true} [:maybe :string]]
   [:user/role role]
   [:user/status status]])

(def CreateUser
  [:map
   [:id {:optional true} id]
   [:email email]
   [:password password]
   [:role role]
   [:status {:optional true} status]])

(def SetPassword
  [:map
   [:password password]])

(def UpdateUser
  [:map
   [:full_name {:optional true} [:maybe :string]]
   [:email {:optional true} email]
   [:role {:optional true} role]
   [:status {:optional true} status]])
