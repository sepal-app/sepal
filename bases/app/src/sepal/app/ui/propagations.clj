(ns sepal.app.ui.propagations
  "The propagation section the material, accession and location panels share.

  One namespace because the three screens show the same thing from different
  angles -- what was grown from this plant, from this accession, on this bench
  -- and three copies would drift."
  (:require [clojure.string :as str]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.propagation.shared :as shared]
            [sepal.app.ui.resource-panel :as panel]
            [zodiac.core :as z]))

(defn propagation-name
  "`Cutting · In progress`, the two facts that tell one batch from another."
  [propagation type-labels status-labels]
  (str (shared/label type-labels (:propagation/type propagation))
       " · "
       (shared/label status-labels (:propagation/status propagation))))

(defn origin-line
  "The one line that says a record was grown here rather than arrived.

  The parent is named because that is the fact the line exists for: a
  propagation link alone would say how, not from what."
  [origin parent type-labels]
  [:p {:class "px-4 py-2 text-sm"}
   "Grown from "
   (if parent
     [:a {:href (z/url-for accession.routes/detail {:id (:accession/id parent)})
          :class "spl-link"}
      (:accession/code parent)]
     "an unrecorded parent")
   " by "
   (str/lower-case (shared/label type-labels (:propagation/type origin)))
   " — "
   [:a {:href (z/url-for propagation.routes/detail {:id (:propagation/id origin)})
        :class "spl-link"}
    "the propagation"]])

(defn panel-section
  "A collapsible list of propagation records.

  Options:

  - :propagations    rows to list, newest first
  - :origin          the propagation this record came from, when there is one
  - :origin-parent   the accession the origin came off, for the line
  - :type-labels     name -> label for the method vocabulary
  - :status-labels   name -> label for the status vocabulary
  - :title           section heading
  - :empty-label     what an empty section says"
  [& {:keys [propagations origin origin-parent type-labels status-labels
             title empty-label]
      :or {title "Propagation"}}]
  (panel/collapsible-section
    :title title
    :count (count propagations)
    :disabled? (and (empty? propagations) (nil? origin))
    :empty-label (or empty-label "none")
    :children
    (list
      (when origin
        (origin-line origin origin-parent type-labels))
      [:div {:class "space-y-0"}
       (for [propagation propagations]
         ^{:key (:propagation/id propagation)}
         [:a {:href (z/url-for propagation.routes/detail
                               {:id (:propagation/id propagation)})
              :class "flex items-center justify-between text-sm hover:bg-surface-alt -mx-2 px-2 py-1.5 rounded transition-colors"}
          [:span {:class "spl-link"}
           (propagation-name propagation type-labels status-labels)]
          [:span {:class "text-text-soft"}
           (or (:propagation/propagated-on propagation) "")]])])))
