(ns sepal.tag.interface.activity
  (:require [sepal.activity.interface :as activity.i]
            [sepal.store.interface :as store.i]
            [sepal.tag.interface.spec :as spec])
  (:import [java.time Instant]))

(def created :tag/created)
(def updated :tag/updated)
(def deleted :tag/deleted)
(def linked :tag/linked)
(def unlinked :tag/unlinked)

(def TagActivityData
  [:map
   [:tag-id spec/id]
   [:name spec/tag-name]])

(def TagLinkActivityData
  [:map
   [:tag-id spec/id]
   [:name spec/tag-name]
   [:resource-type :string]
   [:resource-id pos-int?]
   ;; `activity.i/get-by-resource` finds a resource's activity by looking for
   ;; a `<resource-type>-id` key in the JSON data (see e.g.
   ;; synonym.activity's :taxon-id), not the generic :resource-id above. Tag
   ;; links can point at any of tag.interface.spec/resource-type's members, so
   ;; all three are declared here, optional, and only the one matching the
   ;; actual resource-type is ever populated.
   [:accession-id {:optional true} pos-int?]
   [:material-id {:optional true} pos-int?]
   [:taxon-id {:optional true} pos-int?]])

(defn create! [db type created-by tag]
  (-> (activity.i/create! db {:type type
                              :created-at (Instant/now)
                              :created-by created-by
                              :data {:tag-id (:tag/id tag)
                                     :name (:tag/name tag)}})
      (update :activity/data #(store.i/coerce TagActivityData %))))

(defn create-link! [db type created-by tag resource-type resource-id]
  (-> (activity.i/create! db {:type type
                              :created-at (Instant/now)
                              :created-by created-by
                              :data (assoc {:tag-id (:tag/id tag)
                                            :name (:tag/name tag)
                                            :resource-type (name resource-type)
                                            :resource-id resource-id}
                                           (keyword (str (name resource-type) "-id"))
                                           resource-id)})
      (update :activity/data #(store.i/coerce TagLinkActivityData %))))

(defmethod activity.i/data-schema created [_] TagActivityData)
(defmethod activity.i/data-schema updated [_] TagActivityData)
(defmethod activity.i/data-schema deleted [_] TagActivityData)
(defmethod activity.i/data-schema linked [_] TagLinkActivityData)
(defmethod activity.i/data-schema unlinked [_] TagLinkActivityData)
