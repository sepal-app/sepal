(ns sepal.contact.interface.search
  "Search field definitions for contacts."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :contact [_]
  {:table [:contact :c]
   :fields
   {;; Direct fields
    :name     {:column :c.name
               :type :fts
               :fts-table :contact_fts
               :label "Name"}

    :email    {:column :c.email
               :type :fts
               :fts-table :contact_fts
               :label "Email"}

    :business {:column :c.business
               :type :fts
               :fts-table :contact_fts
               :label "Business"}

    :address  {:column :c.address
               :type :text
               :label "Address"}

    :id       {:column :c.id
               :type :id
               :label "ID"}

    ;; Date fields
    :created {:column :c.created_at
              :type :date
              :label "Created"}

    :updated {:column :c.updated_at
              :type :date
              :label "Updated"}}})
