(ns sepal.accession.interface.search
  "Search field definitions for accessions."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :accession [_]
  {:table [:accession :a]
   :fields
   {;; Direct fields
    :code   {:column :a.code
             :type :fts
             :fts-table :accession_fts
             :label "Code"}

    :id     {:column :a.id
             :type :id
             :label "ID"}

    :provenance {:column :a.provenance_type
                 :type :enum
                 :values [:wild :cultivated :not_wild :purchase :insufficient_data]
                 :label "Provenance"}

    :private {:column :a.private
              :type :boolean
              :label "Private"}

    ;; Related: taxon (direct FK)
    :taxon    {:column :t.name
               :type :fts
               :fts-table :taxon_fts
               :label "Taxon"
               :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}

    :taxon.id {:column :t.id
               :type :id
               :label "Taxon"
               :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}

    :taxon.rank {:column :t.rank
                 :type :enum
                 :values [:species :genus :family :order]
                 :label "Taxon Rank"
                 :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}

    ;; Related: supplier contact
    :supplier    {:column :c.name
                  :type :text
                  :label "Supplier"
                  :joins [[:contact :c] [:= :c.id :a.supplier_contact_id]]}

    :supplier.id {:column :c.id
                  :type :id
                  :label "Supplier"
                  :joins [[:contact :c] [:= :c.id :a.supplier_contact_id]]}

    ;; Related: location (through material)
    :location    {:column :l.code
                  :type :text
                  :label "Location"
                  :joins [[:material :m] [:= :m.accession_id :a.id]
                          [:location :l] [:= :l.id :m.location_id]]}

    :location.id {:column :l.id
                  :type :id
                  :label "Location"
                  :joins [[:material :m] [:= :m.accession_id :a.id]
                          [:location :l] [:= :l.id :m.location_id]]}

    ;; Related: material type
    :material.type {:column :m.type
                    :type :enum
                    :values [:plant :seed :vegetative :tissue :other]
                    :label "Material Type"
                    :joins [[:material :m] [:= :m.accession_id :a.id]]}

    :material.status {:column :m.status
                      :type :enum
                      :values [:alive :dead]
                      :label "Material Status"
                      :joins [[:material :m] [:= :m.accession_id :a.id]]}

    ;; Date fields
    :created {:column :a.created_at
              :type :date
              :label "Created"}

    :updated {:column :a.updated_at
              :type :date
              :label "Updated"}}})
