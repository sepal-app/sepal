(ns sepal.app.routes.accession.detail.shared
  (:require [sepal.app.features :as features]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.app.ui.tabs :as ui.tabs]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.i18n.interface :refer [tr]]
            [zodiac.core :as z]))

(def general-tab ::general)
(def collection-tab ::collection)
(def media-tab ::media)
(def notes-tab ::notes)
(def tags-tab ::tags)

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
  (let [id (:accession/id accession)]
    (cond-> [(ui.tabs/item (tr "General")
                           {:href (z/url-for accession.routes/detail-general {:id id})
                            :active (= active general-tab)})
             (ui.tabs/item (tr "Collection")
                           (if collection-available?
                             {:href (z/url-for accession.routes/detail-collection {:id id})
                              :active (= active collection-tab)}
                             {:disabled collection-disabled-reason}))]
      (features/enabled? :media)
      (conj (ui.tabs/item (tr "Media")
                          {:href (z/url-for accession.routes/detail-media {:id id})
                           :active (= active media-tab)}))
      true
      (conj (ui.tabs/item (tr "Notes")
                          {:href (z/url-for accession.routes/detail-notes {:id id})
                           :active (= active notes-tab)}))
      (features/enabled? :tags)
      (conj (ui.tabs/item (tr "Tags")
                          {:href (z/url-for accession.routes/detail-tags {:id id})
                           :active (= active tags-tab)})))))

(defn tabs
  ([accession active]
   (tabs accession active true))
  ([accession active collection-available?]
   (ui.tabs/tabs {:label (tr "Accession sections")
                  :items (items :accession accession
                                :active active
                                :collection-available? collection-available?)})))

(defn page
  "An accession's record page. Every section — general, collection, media —
  renders through this, supplying only its own body, so no section can end up
  with different padding or a different header from its siblings."
  [& {:keys [accession taxon active body footer collection-available?]
      :or {collection-available? true}}]
  (pages.record/page
    :wide? (= active media-tab)
    :code (:accession/code accession)
    :name (when (:taxon/name taxon)
            (taxon-name/render (:taxon/name taxon)
                               :author (:taxon/author taxon)))
    :tabs (tabs accession active collection-available?)
    :body body
    :footer footer))

(defn breadcrumbs [taxon accession]
  [[:a {:href (z/url-for taxon.routes/index)}
    (tr "Taxa")]
   [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})}
    (taxon-name/render (:taxon/name taxon))]
   [:a {:href (z/url-for accession.routes/index {} {:taxon-id (:taxon/id taxon)})}
    (tr "Accessions")]
   (:accession/code accession)])

(defn actions
  "The same actions on every one of an accession's sections. Defined here
  rather than per section, which is how the sections diverged in the first
  place.

  :primary is the media section's Upload button. Nothing else varies."
  [& {:keys [accession primary]}]
  (let [id (:accession/id accession)]
    (ui.actions/menu
      :primary primary
      :items (cond-> [{:label (tr "Add material")
                       :href (z/url-for material.routes/new nil {:accession-id id})}]
               (features/enabled? :propagation)
               (conj {:label (tr "Add a propagation")
                      :href (z/url-for propagation.routes/new nil {:parent-accession-id id})}))
      :delete-url (z/url-for accession.routes/delete {:id id}))))
