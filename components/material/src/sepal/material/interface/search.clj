(ns sepal.material.interface.search
  "Search field definitions for materials."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :material [_]
  {:table [:material :m]
   :fields
   {;; Direct fields
    :code   {:column :m.code
             :type :text
             :label "Code"}

    :type   {:column :m.type
             :type :enum
             :values [:plant :seed :vegetative :tissue :other]
             :label "Type"}

    :status {:column :m.status
             :type :enum
             :values [:alive :dead]
             :label "Status"}

    :id     {:column :m.id
             :type :id
             :label "ID"}

    ;; Related: accession (direct FK)
    :accession    {:column :a.code
                   :type :text
                   :label "Accession"
                   :joins [[:accession :a] [:= :a.id :m.accession_id]]}

    :accession.id {:column :a.id
                   :type :id
                   :label "Accession"
                   :joins [[:accession :a] [:= :a.id :m.accession_id]]}

    ;; Related: taxon (through accession)
    :taxon       {:column :t.name
                  :type :fts
                  :fts-table :taxon_fts
                  :label "Taxon"
                  :joins [[:accession :a] [:= :a.id :m.accession_id]
                          [:taxon :t] [:= :t.id :a.taxon_id]]}

    :taxon.id    {:column :t.id
                  :type :id
                  :label "Taxon"
                  :joins [[:accession :a] [:= :a.id :m.accession_id]
                          [:taxon :t] [:= :t.id :a.taxon_id]]}

    ;; Related: location (direct FK)
    :location.code {:column :l.code
                    :type :text
                    :label "Location Code"
                    :joins [[:location :l] [:= :l.id :m.location_id]]}

    :location.name {:column :l.name
                    :type :text
                    :label "Location Name"
                    :joins [[:location :l] [:= :l.id :m.location_id]]}

    :location.id {:column :l.id
                  :type :id
                  :label "Location"
                  :joins [[:location :l] [:= :l.id :m.location_id]]}

    ;; Date fields
    :created {:column :m.created_at
              :type :date
              :label "Created"}

    :updated {:column :m.updated_at
              :type :date
              :label "Updated"}}})
