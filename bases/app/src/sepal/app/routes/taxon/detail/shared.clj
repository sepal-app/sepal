(ns sepal.app.routes.taxon.detail.shared
  (:require [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.app.ui.tabs :as ui.tabs]
            [sepal.app.ui.taxon-name :as taxon-name]
            [zodiac.core :as z]))

(def name-tab ::name)
(def media-tab ::media)
(def synonyms-tab ::synonyms)

(defn items [& {:keys [active taxon]}]
  [(ui.tabs/item "Name"
                 {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                  :active (= active name-tab)})
   (ui.tabs/item "Media"
                 {:href (z/url-for taxon.routes/detail-media {:id (:taxon/id taxon)})
                  :active (= active media-tab)})
   (ui.tabs/item "Synonyms"
                 {:href (z/url-for taxon.routes/detail-synonyms {:id (:taxon/id taxon)})
                  :active (= active synonyms-tab)})])

(defn tabs [taxon active]
  (ui.tabs/tabs {:label "Taxon sections"
                 :items (items :taxon taxon :active active)}))

(defn page
  "A taxon's record page. Both sections render through the shared shell, so
  neither can drift from the other. The rank fills the identifier slot: a taxon
  has no code, and its rank is what qualifies the name above it."
  [& {:keys [taxon active body footer]}]
  (pages.record/page
    :code (some-> (:taxon/rank taxon) clojure.core/name)
    :name (taxon-name/render (:taxon/name taxon) :author (:taxon/author taxon))
    :tabs (tabs taxon active)
    :body body
    :footer footer))

(defn breadcrumbs [taxon]
  [[:a {:href (z/url-for taxon.routes/index)} "Taxa"]
   [:span (taxon-name/render (:taxon/name taxon))]])
