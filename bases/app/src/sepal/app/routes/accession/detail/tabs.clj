(ns sepal.app.routes.accession.detail.tabs
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [zodiac.core :as z]))

(defn items [& {:keys [accession active]}]
  [{:label "General"
    :href (z/url-for accession.routes/detail-general {:id (:accession/id accession)})
    :active (= active :general)}
   {:label "Media"
    :href (z/url-for accession.routes/detail-media {:id (:accession/id accession)})
    :active (= active :media)}])
