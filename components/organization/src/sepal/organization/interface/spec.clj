(ns sepal.organization.interface.spec
  (:refer-clojure :exclude [name])
  (:require [camel-snake-kebab.core :as csk]
            [malli.util :as mu]
            [sepal.database.interface :as db.i]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def name :string)
(def short-name :string)
(def abbreviation :string)

(def role [:enum :owner :admin :write :read :guest])

(def Organization
  [:map
   [:organization/id id]
   [:organization/name name]
   [:organization/short-name [:maybe short-name]]
   [:organization/abbreviation [:maybe abbreviation]]])

(def CreateOrganization
  [:map {:closed true
         :store/result Organization}
   [:id {:optional true} id]
   [:name name]
   [:short-name {:optional true} [:maybe short-name]]
   [:abbreviation {:optional true} [:maybe abbreviation]]])

(def OrganizationUser
  [:map
   [:organization-user/organization-id id]
   [:organization-user/user-id id]
   [:organization-user/role {:decode/store csk/->kebab-case-keyword}
    role]])

(def CreateOrganizationUser
  [:map {:closed true
         :store/result OrganizationUser}
   [:organization-id {:decode/store validate.i/coerce-int}
    id]
   [:user-id {:decode/store validate.i/coerce-int}
    id]
   [:role {:decode/store csk/->kebab-case-keyword
           :encode/store (comp db.i/->pg-enum
                               csk/->kebab-case-string)}
    role]])
