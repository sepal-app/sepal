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

    ;; Related: parent taxon.
    ;;
    ;; Filtered through t.parent_id, which the row already carries, so this
    ;; needs no join of its own. As :text against the joined p.name it was
    ;; LIKE '%value%' -- an index serves no leading wildcard -- and the list
    ;; page left-joins p to show the parent's name, so the filter sat on the
    ;; right side of that join and SQLite had to read all 452k taxa to apply
    ;; it. 200ms, against 0.34ms driving from taxon_parent_id_idx.
    ;;
    ;; Matching is by whole word with a trailing prefix now rather than any
    ;; substring, the same as the name search: `parent:quer` finds Quercus and
    ;; `parent:uercu` no longer does.
    :parent    {:column :p.name
                :type :fts
                :fts-table :taxon_fts
                :id-column :t.parent_id
                :label "Parent"}

    :parent.id {:column :p.id
                :type :id
                :label "Parent"
                :joins [[:taxon :p] [:= :p.id :t.parent_id]]}

    ;; Related: what a hybrid was crossed from, which is not its parent.
    ;;
    ;; Through taxon_fts keyed by the parent id the join carries, the same
    ;; shape `parent` uses — an FTS rowid is a taxon id, so the parent's name
    ;; needs no second join to `taxon`. Matching is whole-word with a trailing
    ;; prefix, so `parentage:catt` finds a cross from Cattleya.
    ;;
    ;; The join multiplies a taxon by its number of parents; the compiler
    ;; switches to select-distinct whenever a field brings joins, so a cross
    ;; of four genera is still one row.
    :parentage {:column :pt.name
                :type :fts
                :fts-table :taxon_fts
                :id-column :pg.parent_taxon_id
                :label "Parentage"
                :joins [[:taxon_parentage :pg] [:= :pg.taxon_id :t.id]]}

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
                 :label "Accessions"}

    ;; Related: tag (through tag_link)
    :tag {:column :tg.name
          :type :text
          :label "Tag"
          :joins [[:tag_link :tl] [:and [:= :tl.resource_id :t.id]
                                   [:= :tl.resource_type "taxon"]]
                  [:tag :tg] [:= :tg.id :tl.tag_id]]}

    ;; Resolved by the route, not by the compiler, and so deliberately has no
    ;; :column. A taxon's synonyms live in two places -- the garden's own
    ;; taxon_synonym table and the shared read-only WFO reference file, which is
    ;; a separate SQLite on a separate connection pool -- so there is no join
    ;; the compiler could generate. sepal.app.routes.taxon.index strips this
    ;; filter from the AST before compiling and conjoins the taxon ids it
    ;; resolves to; see the comment there.
    ;;
    ;; It is registered here only so the toolbar's field dropdown offers it:
    ;; search.i/field-options reads this same map. Do not give it a :column
    ;; expecting the compiler to handle it, and do not remove the route's strip
    ;; expecting `:when field-def` to skip it -- being in this map is exactly
    ;; what stops that skip from happening.
    :synonym {:type :text
              :label "Synonym"}}})
