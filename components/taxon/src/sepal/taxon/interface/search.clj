(ns sepal.taxon.interface.search
  "Search field definitions for taxa."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :taxon [_]
  {:table [:taxon :t]
   :fields
   {;; Direct fields
    :name   {:column :t.name
             :type :fts
             :fts-table :taxon_fts
             :label "Name"}

    :author {:column :t.author
             :type :text
             :label "Author"}

    :rank   {:column :t.rank
             :type :enum
             :values [:kingdom :phylum :class :order :family :genus
                      :species :subspecies :variety :form]
             :label "Rank"}

    :id     {:column :t.id
             :type :id
             :label "ID"}

    ;; Related: parent taxon
    :parent    {:column :p.name
                :type :text
                :label "Parent"
                :joins [[:taxon :p] [:= :p.id :t.parent_id]]}

    :parent.id {:column :p.id
                :type :id
                :label "Parent"
                :joins [[:taxon :p] [:= :p.id :t.parent_id]]}

    ;; Related: "has materials of type X" (through accession → material)
    :material.type {:column :m.type
                    :type :enum
                    :values [:plant :seed :vegetative :tissue :other]
                    :label "Material Type"
                    :joins [[:accession :a] [:= :a.taxon_id :t.id]
                            [:material :m] [:= :m.accession_id :a.id]]}

    ;; Related: "has materials with status X"
    :material.status {:column :m.status
                      :type :enum
                      :values [:alive :dead]
                      :label "Material Status"
                      :joins [[:accession :a] [:= :a.taxon_id :t.id]
                              [:material :m] [:= :m.accession_id :a.id]]}

    ;; Related: "has materials at location X"
    :location.code {:column :l.code
                    :type :text
                    :label "Location Code"
                    :joins [[:accession :a] [:= :a.taxon_id :t.id]
                            [:material :m] [:= :m.accession_id :a.id]
                            [:location :l] [:= :l.id :m.location_id]]}

    :location.name {:column :l.name
                    :type :text
                    :label "Location Name"
                    :joins [[:accession :a] [:= :a.taxon_id :t.id]
                            [:material :m] [:= :m.accession_id :a.id]
                            [:location :l] [:= :l.id :m.location_id]]}

    :location.id {:column :l.id
                  :type :id
                  :label "Location"
                  :joins [[:accession :a] [:= :a.taxon_id :t.id]
                          [:material :m] [:= :m.accession_id :a.id]
                          [:location :l] [:= :l.id :m.location_id]]}

    ;; Count fields - use >0 for "has any", =0 for "has none"
    :accessions {:column [:= :accession.taxon_id :t.id]  ; join condition for subquery
                 :type :count
                 :fts-table :accession  ; table to count (reusing fts-table key)
                 :label "Accessions"}}})
