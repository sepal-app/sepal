(ns sepal.app.routes.accession.detail.tabs
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(def general-tab ::general)
(def media-tab ::media)

(defn items [& {:keys [accession active]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
                  :active (= active general-tab)
                  :x-on:click "tabClicked"})
   #_(ui.tabs/item "Source"
                   {:href (z/url-for accession.routes/detail-source {:id (:accession/id accession)})
                    :active (= active source)
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
