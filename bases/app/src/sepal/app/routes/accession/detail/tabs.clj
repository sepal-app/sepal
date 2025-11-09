(ns sepal.app.routes.accession.detail.tabs
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(defn items [& {:keys [accession active]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
                  :active (= active :general)
                  :x-on:click "tabClicked"})
   #_(ui.tabs/item "Source"
                   {:href (z/url-for accession.routes/detail-source {:id (:accession/id accession)})
                    :active (= active :source)
                    :x-on:click "tabClicked"})
   (ui.tabs/item "Media"
                 {:href (z/url-for accession.routes/detail-media {:id (:accession/id accession)})
                  :active (= active :media)
                  :x-on:click "tabClicked"})])
