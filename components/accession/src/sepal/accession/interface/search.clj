(ns sepal.accession.interface.search
  "Search field definitions for accessions."
  (:require [sepal.i18n.interface :refer [N_]]
            [sepal.location.interface :as loc.i]
            [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :accession [_]
  {:table [:accession :a]
   :fields
   {;; Direct fields
    ;; A bare word searches the code and the taxon name, so an accession
    ;; picker finds "quercus" without the taxon: prefix.
    :code   {:column :a.code
             :type :fts
             :fts-table :accession_fts
             :search? true
             :label (N_ "Code")}

    :id     {:column :a.id
             :type :id
             :label (N_ "ID")}

    :provenance {:column :a.provenance_type
                 :type :enum
                 :values [:wild :cultivated :not_wild :purchase :insufficient_data]
                 :label (N_ "Provenance")}

    :private {:column :a.private
              :type :boolean
              :label (N_ "Private")}

    ;; Related: taxon (direct FK)
    :taxon    {:column :t.name
               :type :fts
               :fts-table :taxon_fts
               :search? true
               :label (N_ "Taxon")
               :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}

    :taxon.id {:column :t.id
               :type :id
               :label (N_ "Taxon")
               :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}

    :taxon.rank {:column :t.rank
                 :type :enum
                 :values [:species :genus :family :order]
                 :label (N_ "Taxon Rank")
                 :joins [[:taxon :t] [:= :t.id :a.taxon_id]]}

    ;; Related: supplier contact
    :supplier    {:column :c.name
                  :type :text
                  :label (N_ "Supplier")
                  :joins [[:contact :c] [:= :c.id :a.supplier_contact_id]]}

    :supplier.id {:column :c.id
                  :type :id
                  :label (N_ "Supplier")
                  :joins [[:contact :c] [:= :c.id :a.supplier_contact_id]]}

    ;; Related: location (through material). A location filter covers its
    ;; sub-locations; see loc.i/subtree-filter.
    :location    {:column :l.code
                  :type :text
                  :label (N_ "Location")
                  :filter-clause (loc.i/subtree-filter :m.location_id
                                                       {:column :l.code :type :text})
                  :joins [[:material :m] [:= :m.accession_id :a.id]]}

    :location.id {:column :l.id
                  :type :id
                  :label (N_ "Location")
                  :filter-clause (loc.i/subtree-filter :m.location_id
                                                       {:column :l.id :type :id})
                  :joins [[:material :m] [:= :m.accession_id :a.id]]}

    ;; Related: material type
    :material.type {:column :m.type
                    :type :enum
                    :values [:plant :seed :vegetative :tissue :other]
                    :label (N_ "Material Type")
                    :joins [[:material :m] [:= :m.accession_id :a.id]]}

    :material.status {:column :m.status
                      :type :enum
                      :values [:alive :dead]
                      :label (N_ "Material Status")
                      :joins [[:material :m] [:= :m.accession_id :a.id]]}

    ;; Related: tag (through tag_link)
    :tag {:column :tg.name
          :type :text
          :label (N_ "Tag")
          :joins [[:tag_link :tl] [:and [:= :tl.resource_id :a.id]
                                   [:= :tl.resource_type "accession"]]
                  [:tag :tg] [:= :tg.id :tl.tag_id]]}

    ;; Date fields
    :created {:column :a.created_at
              :type :date
              :label (N_ "Created")}

    :updated {:column :a.updated_at
              :type :date
              :label (N_ "Updated")}}})
