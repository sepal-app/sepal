(ns sepal.app.routes.propagation.shared
  "Helpers the propagation screens share: labels for the seeded vocabularies
  and the names of what a batch produced."
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.propagation.interface :as propagation.i]
            [zodiac.core :as z]))

(defn label-map
  "name -> label for a seeded vocabulary, so the screens render the label the
  lookup table carries rather than the snake_case key."
  [rows name-key label-key]
  (into {} (map (juxt name-key label-key)) rows))

(defn type-labels [db]
  (label-map (propagation.i/list-types db)
             :propagation-type/name
             :propagation-type/label))

(defn status-labels [db]
  (label-map (propagation.i/list-statuses db)
             :propagation-status/name
             :propagation-status/label))

(defn label [labels value]
  (or (get labels value) value))

(defn parent-name
  "`2026.0042` when the parent is the accession alone, `2026.0042.1` when an
  individual plant narrows it."
  [parent parent-material]
  (str (:accession/code parent)
       (when parent-material
         (str "." (:material/code parent-material)))))

(defn product-links
  "What exists now: the material and accessions a propagation produced.

  A material product is material of the parent accession -- the clone case --
  so it is named with the parent's code. An accession product carries its own."
  [parent-code material-products accession-products]
  (concat
    (for [material material-products]
      {:label (str parent-code "." (:material/code material))
       :href (z/url-for material.routes/detail {:id (:material/id material)})})
    (for [accession accession-products]
      {:label (:accession/code accession)
       :href (z/url-for accession.routes/detail {:id (:accession/id accession)})})))
