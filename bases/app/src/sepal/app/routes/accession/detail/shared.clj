(ns sepal.app.routes.accession.detail.shared
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(def general-tab ::general)
(def collection-tab ::collection)
(def media-tab ::media)

(defn items [& {:keys [accession active]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
                  :active (= active general-tab)
                  :x-on:click "tabClicked"})
   (ui.tabs/item "Collection"
                 {:href (z/url-for accession.routes/detail-collection {:id (:accession/id accession)})
                  :active (= active collection-tab)
                  :x-on:click "tabClicked"})
   (ui.tabs/item "Media"
                 {:href (z/url-for accession.routes/detail-media {:id (:accession/id accession)})
                  :active (= active media-tab)
                  :x-on:click "tabClicked"})])

(defn tabs [accession active]
  [:div {:class "flex flex-row justify-center"
         :x-data "accessionTabs"}
   (ui.tabs/tabs (items :accession accession
                        :active active))])

(defn breadcrumbs [taxon accession]
  [[:a {:href (z/url-for taxon.routes/index)}
    "Taxa"]
   [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
        :class "italic"}
    (:taxon/name taxon)]
   [:a {:href (z/url-for accession.routes/index {} {:taxon-id (:taxon/id taxon)})}
    "Accessions"]
   (:accession/code accession)])
