(ns sepal.organization.interface.spec
  (:refer-clojure :exclude [name]))

(def id :int)
(def name :string)
(def short-name :string)
(def abbreviation :string)

(def CreateOrganization
  [:map
   [:name name]
   [:short-name {:optional true} [:maybe short-name]]
   [:abbreviation {:optional true} [:maybe abbreviation]]])

(def Organization
  [:map
   [:organization/id id]
   [:organization/name name]
   [:organization/short-name short-name]
   [:organization/abbreviation abbreviation]])
