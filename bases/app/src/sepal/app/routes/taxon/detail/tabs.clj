(ns sepal.app.routes.taxon.detail.tabs
  (:require [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(def name-tab ::name)
(def media-tab ::media)

(defn items [& {:keys [active taxon]}]
  [(ui.tabs/item "Name"
                 {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                  :active (= active name-tab)})
   (ui.tabs/item "Media"
                 {:href (z/url-for taxon.routes/detail-media {:id (:taxon/id taxon)})
                  :active (= active media-tab)})])

(defn tabs [taxon active]
  [:div {:class "flex flex-row justify-center"
         :x-data "taxonTabs"}
   (ui.tabs/tabs (items :taxon taxon
                        :active active))])
