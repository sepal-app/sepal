(ns sepal.app.routes.taxon.detail.shared
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.app.ui.tabs :as ui.tabs]
            [sepal.app.ui.taxon-name :as taxon-name]
            [zodiac.core :as z]))

(def name-tab ::name)
(def media-tab ::media)
(def synonyms-tab ::synonyms)
(def notes-tab ::notes)
(def tags-tab ::tags)

(defn items [& {:keys [active taxon]}]
  [(ui.tabs/item "Name"
                 {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                  :active (= active name-tab)})
   (ui.tabs/item "Media"
                 {:href (z/url-for taxon.routes/detail-media {:id (:taxon/id taxon)})
                  :active (= active media-tab)})
   (ui.tabs/item "Synonyms"
                 {:href (z/url-for taxon.routes/detail-synonyms {:id (:taxon/id taxon)})
                  :active (= active synonyms-tab)})
   (ui.tabs/item "Notes"
                 {:href (z/url-for taxon.routes/detail-notes {:id (:taxon/id taxon)})
                  :active (= active notes-tab)})
   (ui.tabs/item "Tags"
                 {:href (z/url-for taxon.routes/detail-tags {:id (:taxon/id taxon)})
                  :active (= active tags-tab)})])

(defn tabs [taxon active]
  (ui.tabs/tabs {:label "Taxon sections"
                 :items (items :taxon taxon :active active)}))

(defn page
  "A taxon's record page. All sections render through the shared shell, so
  none can drift from the others. The rank fills the identifier slot: a taxon
  has no code, and its rank is what qualifies the name above it."
  [& {:keys [taxon active body footer]}]
  (pages.record/page
    :wide? (= active media-tab)
    :code (some-> (:taxon/rank taxon) clojure.core/name)
    :name (taxon-name/render (:taxon/name taxon) :author (:taxon/author taxon))
    :tabs (tabs taxon active)
    :body body
    :footer footer))

(defn breadcrumbs [taxon]
  [[:a {:href (z/url-for taxon.routes/index)} "Taxa"]
   [:span (taxon-name/render (:taxon/name taxon))]])

(defn actions
  "The same actions on every one of a taxon's sections. Defined here rather
  than per section, which is how the sections diverged in the first place.

  :primary is the media section's Upload button. Nothing else varies."
  [& {:keys [taxon primary]}]
  (let [id (:taxon/id taxon)]
    (ui.actions/menu
      :primary primary
      :items [{:label "Add an accession"
               :href (z/url-for accession.routes/new nil {:taxon-id id})}
              {:label "Add a child taxon"
               :href (z/url-for taxon.routes/new nil {:parent-id id})}]
      :delete-url (z/url-for taxon.routes/delete {:id id}))))
