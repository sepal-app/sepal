(ns sepal.organization.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.organization.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :organization/created)
(def deleted :organization/deleted)
(def updated :organization/updated)

(defn create! [db type created-by data]
  (activity.i/create! db
                      {:type type
                       :created-at (Instant/now)
                       :created-by created-by
                       :organization-id (:organization/id data)
                       :data {:organization-id (:organization/id data)
                              :organization-name (:organization/name data)
                              :organization-abbreviation (:organization/abbreviation data)}}))

(def OrganizationActivityData
  [:map
   [:organization-id spec/id]
   [:organization-name spec/name]
   [:organization-abbreviation spec/abbreviation]])

(defmethod activity.i/data-schema created [_]
  OrganizationActivityData)

(defmethod activity.i/data-schema updated [_]
  OrganizationActivityData)

(defmethod activity.i/data-schema deleted [_]
  OrganizationActivityData)
