(ns sepal.propagation.interface.search
  "Search field definitions for propagations."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :propagation [_]
  {:table [:propagation :p]
   :fields
   {;; Direct fields
    :id     {:column :p.id
             :type :id
             :label "ID"}

    :status {:column :p.status
             :type :enum
             :values [:active :complete :failed]
             :label "Status"}

    :type   {:column :p.type
             :type :enum
             :values [:seed :cutting :division :graft :layering
                      :tissue_culture :other]
             :label "Type"}

    ;; Related: location (direct FK). A nursery bench is a location, so the
    ;; filter is the bench's name, matched through location_fts the way the
    ;; location list matches its own.
    :location    {:column :l.name
                  :type :fts
                  :fts-table :location_fts
                  :label "Location"
                  :joins [[:location :l] [:= :l.id :p.location_id]]}

    :location.id {:column :p.location_id
                  :type :id
                  :label "Location"}

    ;; Related: the parent accession (direct FK).
    ;;
    ;; Keyed by the foreign key the propagation already carries, so a filter
    ;; needs no join: "what came off 2026.0042" is a rowid match against
    ;; accession_fts. A bare word searches it, because the parent accession is
    ;; what a propagation is known by.
    :accession    {:column :a.code
                   :type :fts
                   :fts-table :accession_fts
                   :id-column :p.parent_accession_id
                   :search? true
                   :label "Parent accession"}

    :accession.id {:column :p.parent_accession_id
                   :type :id
                   :label "Parent accession"}

    ;; Related: the parent's taxon (through the parent accession). "Every
    ;; propagation of Cattleya" is the reporting question the outcome pair
    ;; exists to answer.
    :taxon    {:column :t.name
               :type :fts
               :fts-table :taxon_fts
               :search? true
               :label "Taxon"
               :joins [[:accession :a] [:= :a.id :p.parent_accession_id]
                       [:taxon :t] [:= :t.id :a.taxon_id]]}

    :taxon.id {:column :t.id
               :type :id
               :label "Taxon"
               :joins [[:accession :a] [:= :a.id :p.parent_accession_id]
                       [:taxon :t] [:= :t.id :a.taxon_id]]}

    ;; Related: the rootstock cultivar (direct FK to taxon)
    :rootstock {:column :rt.name
                :type :fts
                :fts-table :taxon_fts
                :label "Rootstock"
                :joins [[:taxon :rt] [:= :rt.id :p.rootstock_taxon_id]]}

    ;; Date fields
    :propagated {:column :p.propagated_on
                 :type :date
                 :label "Propagated"}

    :succeeded {:column :p.succeeded_on
                :type :date
                :label "Succeeded"}

    :created {:column :p.created_at
              :type :date
              :label "Created"}

    :updated {:column :p.updated_at
              :type :date
              :label "Updated"}}})
