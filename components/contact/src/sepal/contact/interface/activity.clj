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
   [:contact-name spec/name]
   [:contact-business spec/business]
   [:changes {:optional true} [:sequential :string]]])

(defn create!
  ([db type created-by data]
   (create! db type created-by data nil))
  ([db type created-by data changes]
   (-> (activity.i/create! db
                           {:type type
                            :created-at (Instant/now)
                            :created-by created-by
                            :resource-type :contact
                            :resource-id (:contact/id data)
                            :data (cond-> {:contact-name (:contact/name data)
                                           :contact-business (:contact/business data)}
                                    changes (assoc :changes changes))})
       (update :activity/data #(store.i/coerce ContactActivityData %)))))

(defmethod activity.i/data-schema created [_]
  ContactActivityData)

(defmethod activity.i/data-schema updated [_]
  ContactActivityData)

(defmethod activity.i/data-schema deleted [_]
  ContactActivityData)
