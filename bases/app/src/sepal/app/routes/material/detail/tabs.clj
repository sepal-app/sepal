(ns sepal.app.routes.material.detail.tabs
  (:require [sepal.app.routes.material.routes :as material.routes]
            [zodiac.core :as z]))

(defn items [& {:keys [active material]}]
  [{:label "General"
    :href (z/url-for material.routes/detail-general {:id (:material/id material)})
    :active (= active :general)}
   {:label "Media"
    :key :media
    :href (z/url-for material.routes/detail-media {:id (:material/id material)})
    :active (= active :media)}])
