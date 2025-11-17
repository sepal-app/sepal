(ns sepal.contact.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.contact.interface.spec :as spec]
            [sepal.store.interface :as store.i])
  (:import [java.time Instant]))

(def created :contact/created)
(def deleted :contact/deleted)
(def updated :contact/updated)

(def ContactActivityData
  [:map
   [:contact-id spec/id]
   [:contact-name spec/name]
   [:contact-business spec/business]])

(defn create! [db type created-by data]
  (-> (activity.i/create! db
                          {:type type
                           :created-at (Instant/now)
                           :created-by created-by
                           :data {:contact-id (:contact/id data)
                                  :contact-name (:contact/name data)
                                  :contact-business (:contact/business data)}})
      (update :activity/data #(store.i/coerce ContactActivityData %))))

(defmethod activity.i/data-schema created [_]
  ContactActivityData)

(defmethod activity.i/data-schema updated [_]
  ContactActivityData)

(defmethod activity.i/data-schema deleted [_]
  ContactActivityData)
