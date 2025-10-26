(ns sepal.malli.core
  (:require ;;[banzai.malli.core :as core]
    [clojure.data.json :as json]
    [malli.core :as m]
    [malli.error :as me]
    [malli.experimental.time :as met]
    [malli.experimental.time.generator]
    [malli.registry :as mr]))

(defn init []
  (mr/set-default-registry!
    (mr/composite-registry
      (m/default-schemas)
      ;; Add the malli.experimental.time schema to the registry
      (met/schemas)
      ;; Add custom global schema here
      {:json
       [:or {:decode/store json/read-str
             :encode/store json/write-str}
        [:map-of :string :any]
        [:sequential [:map-of :string :any]]]})))

(defn humanize-coercion-ex
  "Return the humanized error of a malli.core/coercion exception."
  [ex]
  (-> ex ex-data :data :explain me/humanize))
