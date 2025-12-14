(ns sepal.app.authorization
  "Role-based authorization. Permissions defined as data for easy checking."
  (:require [sepal.accession.interface.permission :as accession.perm]
            [sepal.taxon.interface.permission :as taxon.perm]
            [sepal.location.interface.permission :as location.perm]
            [sepal.contact.interface.permission :as contact.perm]
            [sepal.material.interface.permission :as material.perm]
            [sepal.media.interface.permission :as media.perm]))

;; App-level permissions (non-resource)
(def organization-view ::organization-view)
(def organization-edit ::organization-edit)

(def users-view ::users-view)
(def users-create ::users-create)
(def users-edit ::users-edit)
(def users-delete ::users-delete)
(def users-change-role ::users-change-role)

(def profile-view ::profile-view)
(def profile-edit ::profile-edit)
(def security-view ::security-view)
(def security-edit ::security-edit)

(def activity-view ::activity-view)

(def permissions
  "Map of role -> set of permissions granted to that role."
  {:admin #{organization-view organization-edit
            users-view users-create users-edit users-delete users-change-role
            accession.perm/view accession.perm/create accession.perm/edit accession.perm/delete
            taxon.perm/view taxon.perm/create taxon.perm/edit taxon.perm/delete
            location.perm/view location.perm/create location.perm/edit location.perm/delete
            material.perm/view material.perm/create material.perm/edit material.perm/delete
            contact.perm/view contact.perm/create contact.perm/edit contact.perm/delete
            media.perm/view media.perm/create media.perm/edit media.perm/delete
            profile-view profile-edit
            security-view security-edit
            activity-view}

   :editor #{accession.perm/view accession.perm/create accession.perm/edit accession.perm/delete
             taxon.perm/view taxon.perm/create taxon.perm/edit taxon.perm/delete
             location.perm/view location.perm/create location.perm/edit location.perm/delete
             material.perm/view material.perm/create material.perm/edit material.perm/delete
             contact.perm/view contact.perm/create contact.perm/edit contact.perm/delete
             media.perm/view media.perm/create media.perm/edit media.perm/delete
             profile-view profile-edit
             security-view security-edit
             activity-view}

   :reader #{accession.perm/view
             taxon.perm/view
             location.perm/view
             material.perm/view
             contact.perm/view
             media.perm/view
             profile-view profile-edit
             security-view security-edit
             activity-view}})

(defn has-permission?
  "Check if a role has a specific permission."
  [role permission]
  (contains? (get permissions role) permission))

(defn user-has-permission?
  "Check if a user has a specific permission based on their role."
  [user permission]
  (when-let [role (:user/role user)]
    (has-permission? role permission)))

;; Convenience predicates

(defn admin?
  "Is the user an admin?"
  [user]
  (= :admin (:user/role user)))

(defn editor?
  "Is the user an editor?"
  [user]
  (= :editor (:user/role user)))

(defn reader?
  "Is the user a reader?"
  [user]
  (= :reader (:user/role user)))

(defn can-edit?
  "Can the user edit plant records? (admin or editor)"
  [user]
  (contains? #{:admin :editor} (:user/role user)))
