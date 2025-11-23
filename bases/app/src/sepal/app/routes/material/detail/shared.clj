(ns sepal.app.routes.material.detail.shared
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(def general-tab ::general)
(def media-tab ::media)

(defn- tab-items [& {:keys [active material]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for material.routes/detail-general {:id (:material/id material)})
                  :active (= active general-tab)})
   (ui.tabs/item "Media"
                 {:href (z/url-for material.routes/detail-media {:id (:material/id material)})
                  :active (= active media-tab)})])

(defn tabs [material active]
  [:div {:class "flex flex-row justify-center"
         :x-data "materialTabs"}
   (ui.tabs/tabs (tab-items :material material
                            :active active))])

(defn breadcrumbs [& {:keys [accession material taxon]}]
  [[:a {:href (z/url-for taxon.routes/index)}
    "Taxa"]
   [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
        :class "italic"}
    (:taxon/name taxon)]
   [:a {:href (z/url-for accession.routes/index {} {:taxon-id (:taxon/id taxon)})}
    "Accessions"]
   [:a {:href (z/url-for accession.routes/detail {:id (:accession/id accession)})}
    (:accession/code accession)]
   [:a {:href (z/url-for material.routes/index {} {:accession-id (:accession/id accession)})}
    "Materials"]
   (str (:accession/code accession) "." (:material/code material))])
