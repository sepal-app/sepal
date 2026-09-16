(ns sepal.app.routes.taxon.parent-suggestion
  "The parent a name implies, for the create form to fill in.

  Deliberately narrow. It answers only when the name says what its parent is
  called, exactly one taxon has that name, and that taxon's rank is the one the
  parent's own name implies. Anything less and it answers blank, because
  hanging a taxon off the wrong parent is worse than leaving the field empty.

  The rank check is what `guess-rank` already knows: `Acer palmatum` implies a
  parent named `Acer`, and `Acer` implies a genus, so a taxon named `Acer` that
  is filed as something else is not the parent being described."
  (:require [sepal.app.json :as json]
            [sepal.taxon.interface :as taxon.i]
            [sepal.taxon.interface.name :as taxon.name]
            [zodiac.core :as z]))

(defn suggestion
  "The one taxon that fits, or nil.

  The lookup tries each way the hybrid marker might have been written, because
  the reference taxonomy stores `×` and a keyboard types `x`. Matches are
  pooled across those forms rather than taken from the first that hits: if a
  garden really holds both spellings as separate taxa, that is ambiguous and
  this should say nothing."
  [db taxon-name]
  (when-let [parent (taxon.name/parent-name taxon-name)]
    (let [wanted (taxon.name/guess-rank parent)
          matches (->> (taxon.name/hybrid-marker-variants parent)
                       (mapcat #(taxon.i/list-by-name db %))
                       ;; The same row read twice is the same map, so this is
                       ;; enough to collapse a name that matched two variants.
                       (distinct))]
      (when (and wanted (= 1 (count matches)))
        (let [match (first matches)]
          (when (= wanted (keyword (:taxon/rank match)))
            match))))))

(defn handler [{:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        match (suggestion db (get query-params "name"))]
    (if match
      (json/json-response {:id (:taxon/id match)
                           :text (:taxon/name match)})
      ;; Empty rather than a null body: the client treats anything falsy as
      ;; "no suggestion" and leaves the field alone.
      {:status 200
       :headers {"content-type" "application/json; charset=utf-8"}
       :body ""})))
