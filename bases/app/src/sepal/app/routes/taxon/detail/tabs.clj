(ns sepal.app.routes.taxon.detail.tabs
  (:require [sepal.app.routes.taxon.routes :as taxon.routes]
            [zodiac.core :as z]))

(defn items [& {:keys [active taxon]}]
  [{:label "Name"
    :href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
    :active (= active :name)}
   {:label "Media"
    :href (z/url-for taxon.routes/detail-media {:id (:taxon/id taxon)})
    :active (= active :media)}])
