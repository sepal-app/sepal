(ns sepal.app.search
  "Schema-aware wrappers over `sepal.search.interface`.

  The `:tag` field on the accession, material and taxon search configs joins
  `tag_link`, a table a database below the tag migration does not have. A
  search config is a static `defmethod` with no access to a request, so the
  field cannot be registered conditionally and the gate has to live at the call
  site — here, once, rather than repeated in each of the three index handlers
  and their three export handlers.

  Two things need gating, not one. `field-options` feeds the toolbar's Filter
  dropdown, so an ungated one offers \"Tag\" on a database where choosing it
  500s. `compile-query` is what actually runs, so a hand-typed or bookmarked
  `tag:Fruit` reaches it whether the dropdown offered the field or not."
  (:require [sepal.search.interface :as search.i]
            [sepal.tag.interface :as tag.i]))

(def ^:private tag-field "tag")

(defn field-options
  "`search.i/field-options`, minus fields this database cannot search."
  [ctx resource-type]
  (cond->> (search.i/field-options resource-type)
    (not (tag.i/available? ctx))
    (remove #(= tag-field (:key %)))))

(defn compile-query
  "`search.i/compile-query`, with any `tag:` filter resolved without the table.

  A non-negated `tag:` filter gets a never-matching clause rather than being
  dropped: dropping it would widen the search to every row, the opposite of
  what a filter means. A negated one (`-tag:Fruit`) is dropped, which is the
  right answer — a database with no tag table has nothing tagged, so every row
  satisfies it."
  [ctx resource-type ast base-stmt]
  (if (tag.i/available? ctx)
    (search.i/compile-query resource-type ast base-stmt)
    (let [tag-filters (filter #(= tag-field (:field %)) (:filters ast))
          ast (update ast :filters #(vec (remove (set tag-filters) %)))
          stmt (search.i/compile-query resource-type ast base-stmt)]
      (if (some (complement :negated) tag-filters)
        (update stmt :where #(if % [:and % [:= 1 0]] [:= 1 0]))
        stmt))))
