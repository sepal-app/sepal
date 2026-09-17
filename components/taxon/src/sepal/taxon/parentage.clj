(ns sepal.taxon.parentage
  "What a hybrid was crossed from.

  Separate from `taxon.parent_id`, which is containment: Acer × freemanii is a
  nothospecies in the genus Acer, so its parent is Acer. The cross is the other
  relation — Acer rubrum × Acer saccharinum — and a taxon may name any number
  of parents, because a nothogenus can: × Potinara is four genera."
  (:require [sepal.database.interface :as db.i]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface.spec :as spec]))

(def ^:private columns
  "`taxon_parentage` columns aliased onto the `:parentage/...` namespace the
  spec uses, plus the parent's own name and rank.

  The table's own name would give `:taxon-parentage/...`, which `spec/Parentage`
  does not match. The `x__y` alias shape is what `sepal.database.core/label-fn`
  splits into `:x/y`; a plain qualified keyword renders as `ns.name`, which
  SQLite rejects as an alias."
  [[:p.id :parentage__id]
   [:p.taxon_id :parentage__taxon_id]
   [:p.parent_taxon_id :parentage__parent_taxon_id]
   [:p.role :parentage__role]
   [:p.position :parentage__position]
   [:p.created_by :parentage__created_by]
   [:p.created_at :parentage__created_at]
   [:t.name :parent__name]
   [:t.rank :parent__rank]])

(defn list-for-taxon
  "This taxon's parents, in the order the formula is written.

  Ordered by `position` rather than by name: the convention is seed parent
  first, and the order is what the formula reads back as. Joins the parent
  taxon so a caller rendering the cross does not need a second query per row."
  [db taxon-id]
  (->> (db.i/execute! db {:select columns
                          :from [[:taxon_parentage :p]]
                          :join [[:taxon :t] [:= :t.id :p.parent_taxon_id]]
                          :where [:= :p.taxon_id taxon-id]
                          :order-by [[:p.position :asc]]})
       (mapv (fn [row]
               (-> row
                   (update :parentage/role keyword)
                   (select-keys [:parentage/id :parentage/taxon-id
                                 :parentage/parent-taxon-id :parentage/role
                                 :parentage/position :parent/name
                                 :parent/rank]))))))

(defn list-children
  "The crosses that name this taxon as a parent.

  The other direction of the same table, which is what makes \"what do I hold
  with Cattleya in its parentage\" answerable, and what
  `taxon_parentage_parent_taxon_id_idx` exists for."
  [db parent-taxon-id]
  (db.i/execute! db {:select [[:t.id :taxon__id]
                              [:t.name :taxon__name]
                              [:p.role :parentage__role]]
                     :from [[:taxon_parentage :p]]
                     :join [[:taxon :t] [:= :t.id :p.taxon_id]]
                     :where [:= :p.parent_taxon_id parent-taxon-id]
                     :order-by [[:t.name :asc]]}))

(defn set-for-taxon!
  "Replace this taxon's parentage with `parents`, in the order given.

  Replace rather than merge, because the form posts the whole list the way the
  vernacular-name rows do, and a diff would have to invent identity for a row
  the browser does not number. `position` comes from the order of the
  collection, so a caller never sets it and two parents can never claim one
  slot.

  Runs in one transaction: a half-applied cross is a cross that says something
  false."
  [db taxon-id parents & {:keys [created-by]}]
  (db.i/with-transaction [tx db]
    (db.i/execute-one! tx {:delete-from :taxon_parentage
                           :where [:= :taxon_id taxon-id]})
    (when (seq parents)
      (db.i/execute-one!
        tx {:insert-into :taxon_parentage
            :values (vec (map-indexed
                           (fn [i parent]
                             (let [{:keys [parent-taxon-id role]}
                                   (store.i/encode spec/CreateParentage parent)]
                               {:taxon_id taxon-id
                                :parent_taxon_id parent-taxon-id
                                :role (or role "unknown")
                                :position i
                                :created_by created-by}))
                           parents))}))
    (list-for-taxon tx taxon-id)))
