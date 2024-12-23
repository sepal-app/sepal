(ns sepal.location.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]))

(def id pos-int?)
(def name [:string {:min 2}])
(def code [:string {:min 2}])
(def description :string)

(def Location
  [:map {:closed true}
   [:location/id id]
   [:location/code code]
   [:location/description [:maybe  description]]
   [:location/name name]])

(def CreateLocation
  [:map {:closed true
         :store/result Location}
   [:code code]
   [:name name]
   [:description description]])

(def UpdateLocation
  (mu/optional-keys
    [:map {:closed true
           :store/result Location}
     [:code code]
     [:name name]
     [:description description]]))
