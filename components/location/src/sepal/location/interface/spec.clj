(ns sepal.location.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def organization-id pos-int?)
(def name [:string {:min 2}])
(def code [:string {:min 2}])
(def description :string)

(def Location
  [:map {:closed true}
   [:location/id id]
   [:location/code code]
   [:location/description description]
   [:location/name name]
   [:location/organization-id organization-id]])

(def CreateLocation
  [:map {:closed true
         :store/result Location}
   [:code code]
   [:name name]
   [:description description]
   [:organization-id {:decode/store validate.i/coerce-int}
    organization-id]])

(def UpdateLocation
  (mu/optional-keys
    [:map {:closed true
           :store/result Location}
     [:code code]
     [:name name]
     [:description description]]))
