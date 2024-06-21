(ns sepal.location.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]))

(def id :int)
(def organization-id :int)
(def name [:string {:min 2}])
(def code [:string {:min 2}])
(def description [:string])

(def Location
  [:map {:closed true}
   [:location/id id]
   [:location/code code]
   [:location/description description]
   [:location/name name]
   [:location/organization-id organization-id]])

(defn coerce-int [v]
  (cond
    (int? v) v
    (string? v) (Integer/parseInt v)
    (nil? v) v
    :else (int v)))

(def CreateLocation
  [:map {:closed true}
   [:code code]
   [:name name]
   [:description description]
   [:organization-id {:decode/db coerce-int}
    organization-id]])

(def UpdateLocation
  (mu/optional-keys
   [:map {:closed true}
    [:code code]
    [:name name]
    [:description description]]))
