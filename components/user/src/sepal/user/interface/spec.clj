(ns sepal.user.interface.spec
  (:require [clojure.string :as str]
            [sepal.validation.interface :refer [email-re]]))

(def id pos-int?)

(defn normalize-email
  "Normalize email to lowercase for consistent storage and comparison."
  [email]
  (some-> email str/lower-case))

(def email
  [:re {:error/message "invalid email"
        :encode/store normalize-email
        :decode/form normalize-email}
   email-re])

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
  [:map {:store/columns [:id :email :full-name :role :status]}
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
   [:full-name {:optional true} [:maybe :string]]
   [:status {:optional true} status]])

(def SetPassword
  [:map
   [:password password]])

(def UpdateUser
  [:map
   [:full-name {:optional true} [:maybe :string]]
   [:email {:optional true} email]
   [:role {:optional true} role]
   [:status {:optional true} status]])
