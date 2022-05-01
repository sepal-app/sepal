(ns sepal.user.interface.spec
  (:require [malli.core :as m]
            [sepal.validation.interface :refer [email-re]]))

(def id :int)
(def email [:re email-re])
(def password [:string {:min 10}])

(def CreateUser
  [:map
   [:email email]
   [:password password]])

(def User
  [:map
   [:user/id id]
   [:user/email email]])

;; (comment
;;   (require '[malli.error :as me])
;;   (m/validate [:qualified-keyword {:namespace :aaa}] :aaa/bbb)

;;   (defn v [spec data]
;;     (me/humanize (m/explain spec data)))

;;   (v :int {:abc "x"})
;;   (v [:map [:a :string]] {:abc "x"})

;;   (m/explain [:map [:a :string]] {:abc "x"})

;;   (m/validate [:map {:closed true}
;;                [:user/password password]]
;;               {:user/password "12345678890"
;;                :user/email "abc"})

;;   (let [e (me/humanize (m/explain CreateUser
;;                                   {:user/password "12345678890" :user/email "abc"}))]
;;     (meta
;;      (with-meta e {:error true})))

;;   (->> (validate CreateUser {:email "1234"})
;;        (error?))

;;   (err/invalid-input-error (me/humanize (m/explain spec/CreateAircraft data)))

;;   ::password
;;   ())
