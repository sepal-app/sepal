(ns sepal.app.routes.propagation.shared
  "Helpers the propagation screens share: labels for the seeded vocabularies
  and the names of what a batch produced."
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.code-template.interface :as ct.i]
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

(defn label
  "The label for `value`, which is a keyword on a coerced row and a string on a
  raw one."
  [labels value]
  (let [k (some-> value name)]
    (or (get labels k) k)))

(defn parent-name
  "`2026.0042` when the parent is the accession alone, `2026.0042.1` when an
  individual plant narrows it."
  [parent parent-material separator]
  (if parent-material
    (ct.i/full-code separator (:accession/code parent) (:material/code parent-material))
    (:accession/code parent)))

(defn product-links
  "What exists now: the material and accessions a propagation produced.

  A material product is material of the parent accession -- the clone case --
  so it is named with the parent's code. An accession product carries its own."
  [parent-code separator material-products accession-products]
  (concat
    (for [material material-products]
      {:label (ct.i/full-code separator parent-code (:material/code material))
       :href (z/url-for material.routes/detail {:id (:material/id material)})})
    (for [accession accession-products]
      {:label (:accession/code accession)
       :href (z/url-for accession.routes/detail {:id (:accession/id accession)})})))

(defn products?
  "Whether anything has been recorded as coming out of the batch, from the
  panel data that lists them."
  [panel-data]
  (boolean (or (seq (:material-products panel-data))
               (seq (:accession-products panel-data)))))

(defn actions
  "The actions menu for a propagation.

  The default product comes first. Reaccessioning a clone is deliberate and
  rare -- a research project, signed off by whoever keeps the records -- so it
  sits second rather than with equal weight: fragmenting one genotype across
  accession numbers by accident is the corruption this model exists to
  prevent."
  [propagation default-kind]
  (let [id (:propagation/id propagation)
        product-url (z/url-for propagation.routes/product {:id id})
        status-url (z/url-for propagation.routes/status {:id id})
        other (if (= default-kind :material) :accession :material)
        label {:material "Create material"
               :accession "Create accession"}]
    (ui.actions/menu
      :items (cond-> [{:label (label default-kind)
                       :post-url product-url
                       :params {:kind (name default-kind)}}
                      {:label (if (= other :accession)
                                "Reaccession as a new accession"
                                "Create material under the parent accession")
                       :post-url product-url
                       :params {:kind (name other)}}]
               (= :active (:propagation/status propagation))
               (into [{:label "Mark complete"
                       :post-url status-url
                       :params {:status "complete"}}
                      {:label "Mark failed"
                       :post-url status-url
                       :params {:status "failed"}}]))
      :delete-url (z/url-for propagation.routes/delete {:id id}))))
