(ns sepal.app.routes.material.detail.tabs
  (:require [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(def general-tab ::general)
(def media-tab ::media)

(defn items [& {:keys [active material]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for material.routes/detail-general {:id (:material/id material)})
                  :active (= active general-tab)})
   (ui.tabs/item "Media"
                 {:href (z/url-for material.routes/detail-media {:id (:material/id material)})
                  :active (= active media-tab)})])

(defn tabs [material active]
  [:div {:class "flex flex-row justify-center"
         :x-data "materialTabs"}
   (ui.tabs/tabs (items :accession material
                        :active active))])
