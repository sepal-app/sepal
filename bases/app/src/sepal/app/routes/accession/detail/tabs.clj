(ns sepal.app.routes.accession.detail.tabs
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(defn items [& {:keys [accession active]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
                  :active (= active :general)
                  :x-on:click "tabClicked"})
   (ui.tabs/item "Provenance"
                 {:href (z/url-for accession.routes/detail-provenance {:id (:accession/id accession)})
                  :active (= active :provenance)
                  :x-on:click "tabClicked"})
   (ui.tabs/item "Media"
                 {:href (z/url-for accession.routes/detail-media {:id (:accession/id accession)})
                  :active (= active :media)
                  :x-on:click "tabClicked"})])
