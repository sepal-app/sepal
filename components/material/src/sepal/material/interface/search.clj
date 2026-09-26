(ns sepal.material.interface.search
  "Search field definitions for materials."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :material [_]
  {:table [:material :m]
   :fields
   {;; A bare word searches the three things a material is known by: its own
    ;; code, the code of the accession it came from, and the plant it is. They
    ;; are what the picker shows on one line — "2026.0001.1 (Prunus salicina)"
    ;; — and searching only the last of them meant typing a code you could read
    ;; on screen found nothing.
    :code   {:column :m.code
             :type :text
             :search? true
             :label "Code"}

    :type   {:column :m.type
             :type :enum
             :values [:plant :seed :vegetative :tissue :other]
             :label "Type"}

    :status {:column :m.status
             :type :enum
             :values [:alive :dead :dormant :transferred :other :unknown]
             :label "Status"}

    :id     {:column :m.id
             :type :id
             :label "ID"}

    ;; Related: accession (direct FK).
    ;;
    ;; Through accession_fts keyed by the foreign key the material already
    ;; carries, so this needs no join and matches whole words the way the
    ;; accession list does.
    :accession    {:column :a.code
                   :type :fts
                   :fts-table :accession_fts
                   :id-column :m.accession_id
                   :search? true
                   :label "Accession"}

    :accession.id {:column :a.id
                   :type :id
                   :label "Accession"
                   :joins [[:accession :a] [:= :a.id :m.accession_id]]}

    ;; Related: taxon (through accession)
    :taxon       {:column :t.name
                  :type :fts
                  :fts-table :taxon_fts
                  :search? true
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

    ;; Related: tag (through tag_link)
    :tag {:column :tg.name
          :type :text
          :label "Tag"
          :joins [[:tag_link :tl] [:and [:= :tl.resource_id :m.id]
                                   [:= :tl.resource_type "material"]]
                  [:tag :tg] [:= :tg.id :tl.tag_id]]}

    ;; Date fields
    :created {:column :m.created_at
              :type :date
              :label "Created"}

    :updated {:column :m.updated_at
              :type :date
              :label "Updated"}}})
