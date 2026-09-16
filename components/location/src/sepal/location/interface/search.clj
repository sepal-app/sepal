(ns sepal.location.interface.search
  "Search field definitions for locations."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :location [_]
  {:table [:location :l]
   :fields
   {;; Direct fields
    :code        {:column :l.code
                  :type :fts
                  :fts-table :location_fts
                  :label "Code"}

    :name        {:column :l.name
                  :type :fts
                  :fts-table :location_fts
                  :label "Name"}

    :description {:column :l.description
                  :type :fts
                  :fts-table :location_fts
                  :label "Description"}

    :id          {:column :l.id
                  :type :id
                  :label "ID"}

    ;; A retired location is out of the way unless asked for: `archived:true`
    ;; lists them, `archived:false` the rest, and a query that says neither
    ;; gets the active ones. The column holds a word rather than a flag, so the
    ;; comparison is the column and the boolean tests its result.
    :archived    {:column [:= :l.status "archived"]
                  :type :boolean
                  :label "Archived"}

    ;; Related: "contains taxon X" (through material → accession → taxon)
    :taxon    {:column :t.name
               :type :fts
               :fts-table :taxon_fts
               :label "Taxon"
               :joins [[:material :m] [:= :m.location_id :l.id]
                       [:accession :a] [:= :a.id :m.accession_id]
                       [:taxon :t] [:= :t.id :a.taxon_id]]}

    :taxon.id {:column :t.id
               :type :id
               :label "Taxon"
               :joins [[:material :m] [:= :m.location_id :l.id]
                       [:accession :a] [:= :a.id :m.accession_id]
                       [:taxon :t] [:= :t.id :a.taxon_id]]}

    ;; Related: "contains material type X"
    :material.type {:column :m.type
                    :type :enum
                    :values [:plant :seed :vegetative :tissue :other]
                    :label "Material Type"
                    :joins [[:material :m] [:= :m.location_id :l.id]]}

    :material.status {:column :m.status
                      :type :enum
                      :values [:alive :dead]
                      :label "Material Status"
                      :joins [[:material :m] [:= :m.location_id :l.id]]}

    ;; Date fields
    :created {:column :l.created_at
              :type :date
              :label "Created"}

    :updated {:column :l.updated_at
              :type :date
              :label "Updated"}}})
