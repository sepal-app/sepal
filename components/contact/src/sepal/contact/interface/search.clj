(ns sepal.contact.interface.search
  "Search field definitions for contacts."
  (:require [sepal.i18n.interface :refer [N_]]
            [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :contact [_]
  {:table [:contact :c]
   :fields
   {;; Direct fields
    :name     {:column :c.name
               :type :fts
               :fts-table :contact_fts
               :label (N_ "Name")}

    :email    {:column :c.email
               :type :fts
               :fts-table :contact_fts
               :label (N_ "Email")}

    :business {:column :c.business
               :type :fts
               :fts-table :contact_fts
               :label (N_ "Business")}

    ;; The one-line address a contact saved before the split still carries,
    ;; alongside the fields that replaced it.
    :address  {:column :c.address
               :type :text
               :label (N_ "Address")}

    :address1 {:column :c.address1
               :type :text
               :label (N_ "Address 1")}

    :address2 {:column :c.address2
               :type :text
               :label (N_ "Address 2")}

    :city     {:column :c.city
               :type :text
               :label (N_ "City")}

    :id       {:column :c.id
               :type :id
               :label (N_ "ID")}

    ;; Date fields
    :created {:column :c.created_at
              :type :date
              :label (N_ "Created")}

    :updated {:column :c.updated_at
              :type :date
              :label (N_ "Updated")}}})
