(ns sepal.app.routes.accession.detail.shared
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [sepal.app.ui.taxon-name :as taxon-name]
            [zodiac.core :as z]))

(def general-tab ::general)
(def collection-tab ::collection)
(def media-tab ::media)

(def collection-disabled-reason
  "Available when provenance is wild collected")

(defn collection-available?
  "The Collection tab holds wild-collection data — collector, habitat, locality,
  coordinates — so it is gated on provenance.

  The second clause matters: a tab is also available when the record already has
  data on it. Without it, changing an accession's provenance from wild to
  nursery would strand thirteen filled-in fields behind a tab nobody can open."
  [accession has-collection?]
  (boolean (or has-collection?
               (= :wild (:accession/provenance-type accession)))))

(defn items [& {:keys [accession active collection-available?]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
                  :active (= active general-tab)})
   (ui.tabs/item "Collection"
                 (if collection-available?
                   {:href (z/url-for accession.routes/detail-collection {:id (:accession/id accession)})
                    :active (= active collection-tab)}
                   {:disabled collection-disabled-reason}))
   (ui.tabs/item "Media"
                 {:href (z/url-for accession.routes/detail-media {:id (:accession/id accession)})
                  :active (= active media-tab)})])

(defn tabs
  ([accession active]
   (tabs accession active true))
  ([accession active collection-available?]
   (ui.tabs/tabs {:label "Accession sections"
                  :items (items :accession accession
                                :active active
                                :collection-available? collection-available?)})))

(defn breadcrumbs [taxon accession]
  [[:a {:href (z/url-for taxon.routes/index)}
    "Taxa"]
   [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})}
    (taxon-name/render (:taxon/name taxon))]
   [:a {:href (z/url-for accession.routes/index {} {:taxon-id (:taxon/id taxon)})}
    "Accessions"]
   (:accession/code accession)])
